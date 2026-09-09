package com.example.waterrefillapijava.security;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;

public final class ClientFingerprintUtil {

	private ClientFingerprintUtil() {
	}

	public static String computeFingerprint(
		@NonNull final HttpServletRequest request,
		final String salt,
		final boolean trustProxy
	) {
		final String ip = extractClientIp(request, trustProxy);
		final String ipPrefix = computeIpPrefix(ip);
		final String userAgent = request.getHeader("User-Agent");
		final String raw = (userAgent == null ? "" : userAgent) + "|" + ipPrefix + "|" + (salt == null ? "" : salt);
		return sha256Hex(raw);
	}

	public static String extractClientIp(@NonNull final HttpServletRequest request, final boolean trustProxy) {
		if (!trustProxy) {
			return request.getRemoteAddr();
		}
		final String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded == null || forwarded.isBlank()) {
			return request.getRemoteAddr();
		}
		final String[] parts = forwarded.split(",");
		for (final String part : parts) {
			final String candidate = part.trim();
			if (!candidate.isEmpty() && isValidPublicIp(candidate)) {
				return candidate;
			}
		}
		return request.getRemoteAddr();
	}

	public static String computeIpPrefix(final String ip) {
		if (ip == null || ip.isBlank()) {
			return "";
		}
		final String trimmed = ip.trim();
		if (trimmed.contains(":")) {
			return trimmed;
		}
		final int lastDot = trimmed.lastIndexOf('.');
		if (lastDot == -1) {
			return trimmed;
		}
		return trimmed.substring(0, lastDot + 1) + "*";
	}

	public static boolean constantTimeEquals(final String a, final String b) {
		if (a == null || b == null) {
			return a == null && b == null;
		}
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean isValidPublicIp(@NonNull final String ip) {
		try {
			final InetAddress address = InetAddress.getByName(ip);
			return !address.isLoopbackAddress()
				&& !address.isAnyLocalAddress()
				&& !address.isLinkLocalAddress()
				&& !address.isSiteLocalAddress()
				&& !address.isMulticastAddress();
		} catch (final Exception e) {
			return false;
		}
	}

	private static String sha256Hex(@NonNull final String value) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
