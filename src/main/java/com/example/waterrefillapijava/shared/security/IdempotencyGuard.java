package com.example.waterrefillapijava.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.waterrefillapijava.shared.model.IdempotencyRecord;
import com.example.waterrefillapijava.shared.model.IdempotencyRecord.Status;
import com.example.waterrefillapijava.shared.repository.IdempotencyRecordRepository;

import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IdempotencyGuard {

	private static final Duration RESULT_TTL = Duration.ofMinutes(5);
	private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
	private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);

	private final IdempotencyRecordRepository repository;
	private final TransactionTemplate tx;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<String, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();
	private ScheduledExecutorService cleaner;

	public IdempotencyGuard(
		@NonNull final IdempotencyRecordRepository repository,
		@NonNull final TransactionTemplate tx,
		@NonNull final ObjectMapper objectMapper
	) {
		this.repository = repository;
		this.tx = tx;
		this.objectMapper = objectMapper;
		this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
			final Thread t = new Thread(r, "idempotency-guard-cleaner");
			t.setDaemon(true);
			return t;
		});
		this.cleaner.scheduleWithFixedDelay(this::purgeExpired, 60, 60, TimeUnit.SECONDS);
	}

	@SuppressWarnings("unchecked")
	public <T> T execute(@NonNull final String idempotencyKey, @NonNull final Supplier<T> operation) {
		final CompletableFuture<T>[] holder = new CompletableFuture[1];

		inFlight.compute(idempotencyKey, (key, existing) -> {
			if (existing != null && !existing.isDone()) {
				return existing;
			}
			final CompletableFuture<T> future = new CompletableFuture<>();
			holder[0] = future;
			return future;
		});

		if (holder[0] != null) {
			return executeWithPersistence(idempotencyKey, operation, holder[0]);
		}

		final CompletableFuture<?> future = inFlight.get(idempotencyKey);
		if (future != null) {
			return awaitExisting(future);
		}

		return executeWithPersistence(idempotencyKey, operation, null);
	}

	@SuppressWarnings("unchecked")
	private <T> T executeWithPersistence(
		@NonNull final String idempotencyKey,
		@NonNull final Supplier<T> operation,
		final CompletableFuture<T> future
	) {
		if (future == null) {
			final Optional<IdempotencyRecord> existing = tx.execute(status ->
				repository.findById(idempotencyKey)
			);
			if (existing.isPresent()) {
				final IdempotencyRecord record = existing.get();
				return switch (record.getStatus()) {
					case COMPLETED -> deserialize(record.getResultPayload(), record.getResultType());
					case FAILED -> throw new RuntimeException(record.getResultPayload());
					case IN_PROGRESS -> pollForResult(idempotencyKey);
				};
			}
		}

		final boolean isFirst = future != null;
		final CompletableFuture<T> target = isFirst ? future : new CompletableFuture<>();
		if (!isFirst) {
			inFlight.put(idempotencyKey, target);
		}

		final Instant now = Instant.now();
		final IdempotencyRecord record = new IdempotencyRecord();
		record.setIdempotencyKey(idempotencyKey);
		record.setStatus(Status.IN_PROGRESS);
		record.setCreatedAt(now);
		record.setExpiresAt(now.plus(RESULT_TTL));
		try {
			tx.executeWithoutResult(status -> repository.save(record));
		} catch (final Exception e) {
			log.debug("Idempotency record already exists for key={}", idempotencyKey);
		}

		try {
			final T result = operation.get();
			target.complete(result);
			saveCompleted(idempotencyKey, result);
			return result;
		} catch (final RuntimeException e) {
			target.completeExceptionally(e);
			saveFailed(idempotencyKey, e);
			throw e;
		} finally {
			inFlight.remove(idempotencyKey);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T awaitExisting(@NonNull final CompletableFuture<?> future) {
		try {
			return (T) future.join();
		} catch (final RuntimeException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof final RuntimeException re) {
				throw re;
			}
			throw e;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T pollForResult(@NonNull final String idempotencyKey) {
		final Instant deadline = Instant.now().plus(POLL_TIMEOUT);
		while (Instant.now().isBefore(deadline)) {
			final Optional<IdempotencyRecord> record = tx.execute(status ->
				repository.findById(idempotencyKey)
			);
			if (record.isPresent()) {
				final IdempotencyRecord r = record.get();
				if (r.getStatus() == Status.COMPLETED) {
					return deserialize(r.getResultPayload(), r.getResultType());
				}
				if (r.getStatus() == Status.FAILED) {
					throw new RuntimeException(r.getResultPayload());
				}
			}
			try {
				Thread.sleep(POLL_INTERVAL.toMillis());
			} catch (final InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted waiting for idempotency result", ie);
			}
		}
		throw new RuntimeException("Idempotency poll timed out for key: " + idempotencyKey);
	}

	private <T> void saveCompleted(@NonNull final String key, @NonNull final T result) {
		tx.executeWithoutResult(status -> {
			repository.findById(key).ifPresent(record -> {
				record.setStatus(Status.COMPLETED);
				record.setResultPayload(serialize(result));
				record.setResultType(result.getClass().getName());
				repository.save(record);
			});
		});
	}

	private void saveFailed(@NonNull final String key, @NonNull final RuntimeException exception) {
		tx.executeWithoutResult(status -> {
			repository.findById(key).ifPresent(record -> {
				record.setStatus(Status.FAILED);
				record.setResultPayload(exception.getMessage());
				record.setResultType(exception.getClass().getName());
				repository.save(record);
			});
		});
	}

	private String serialize(@NonNull final Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (final Exception e) {
			log.warn("Failed to serialize idempotency result", e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T deserialize(final String payload, final String typeName) {
		if (payload == null || typeName == null) {
			return null;
		}
		try {
			final Class<?> type = Class.forName(typeName);
			return (T) objectMapper.readValue(payload, type);
		} catch (final Exception e) {
			log.warn("Failed to deserialize idempotency result type={}", typeName, e);
			return null;
		}
	}

	private void purgeExpired() {
		try {
			final Integer deleted = tx.execute(status -> repository.deleteExpiredBefore(Instant.now()));
			if (deleted != null && deleted > 0) {
				log.debug("Purged {} expired idempotency records", deleted);
			}
		} catch (final Exception e) {
			log.warn("Failed to purge expired idempotency records", e);
		}
	}

	@PreDestroy
	public void shutdown() {
		cleaner.shutdownNow();
	}
}
