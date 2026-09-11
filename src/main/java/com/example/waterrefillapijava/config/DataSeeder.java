package com.example.waterrefillapijava.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.MeterReading;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductComponent;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.model.Setting;
import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductComponentRepository;
import com.example.waterrefillapijava.repository.ProductRepository;
import com.example.waterrefillapijava.repository.SettingRepository;
import com.example.waterrefillapijava.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

	private final UserRepository userRepository;
	private final SettingRepository settingRepository;
	private final CustomerRepository customerRepository;
	private final ProductRepository productRepository;
	private final ProductComponentRepository productComponentRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final MeterReadingRepository meterReadingRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(String... args) {
		if (userRepository.count() == 0) {
			final User admin = User.builder()
				.username("admin")
				.password(passwordEncoder.encode("password"))
				.build();
			userRepository.save(admin);
			log.info("Seeded default admin user (username: admin, password: password)");
		}

		if (settingRepository.findById(1L).isEmpty()) {
			final Setting defaults = Setting.builder()
				.id(1L)
				.storeName("My Water Refilling Station")
				.storeAddress("")
				.storePhone("")
				.currency("PHP")
				.lowStockThreshold(10)
				.build();
			settingRepository.save(defaults);
			log.info("Seeded default settings");
		}

		if (customerRepository.count() == 0) {
			customerRepository.save(Customer.builder()
				.name("Juan Dela Cruz").phone("09171234567").email("juan@example.com")
				.subscriberStatus("active").bottleDebt(2).outstandingBalance(new BigDecimal("150")).build());
			customerRepository.save(Customer.builder()
				.name("Maria Santos").phone("09281234567").email("maria@example.com")
				.subscriberStatus("active").bottleDebt(0).outstandingBalance(BigDecimal.ZERO).build());
			customerRepository.save(Customer.builder()
				.name("Pedro Reyes").phone("09391234567").email("pedro@example.com")
				.subscriberStatus("inactive").bottleDebt(5).outstandingBalance(new BigDecimal("400")).build());
			log.info("Seeded 3 default customers");
		}

		if (productRepository.count() == 0) {
			productRepository.save(Product.builder()
				.name("Purified Water").type(ProductType.water_refill)
				.volumeGallons(new BigDecimal("5")).price(new BigDecimal("25"))
				.stockQuantity(100).reorderPoint(20).build());
			productRepository.save(Product.builder()
				.name("Alkaline Water").type(ProductType.water_refill)
				.volumeGallons(new BigDecimal("5")).price(new BigDecimal("35"))
				.stockQuantity(50).reorderPoint(10).build());
			productRepository.save(Product.builder()
				.name("Water Jug 5 Gal").type(ProductType.accessory)
				.price(new BigDecimal("150"))
				.stockQuantity(30).reorderPoint(5).build());
			productRepository.save(Product.builder()
				.name("Cap").type(ProductType.accessory)
				.price(new BigDecimal("2"))
				.stockQuantity(500).reorderPoint(100).build());
			productRepository.save(Product.builder()
				.name("Seal").type(ProductType.accessory)
				.price(new BigDecimal("1"))
				.stockQuantity(500).reorderPoint(100).build());
			productRepository.save(Product.builder()
				.name("Dispenser").type(ProductType.equipment)
				.price(new BigDecimal("500"))
				.stockQuantity(0).reorderPoint(1).build());
			log.info("Seeded 6 default products");
		}

		if (productComponentRepository.count() == 0) {
			final Product purifiedWater = productRepository.findByNameIgnoreCase("Purified Water").orElse(null);
			final Product alkalineWater = productRepository.findByNameIgnoreCase("Alkaline Water").orElse(null);
			final Product cap = productRepository.findByNameIgnoreCase("Cap").orElse(null);
			final Product seal = productRepository.findByNameIgnoreCase("Seal").orElse(null);

			if (purifiedWater != null && cap != null && seal != null) {
				productComponentRepository.save(ProductComponent.builder()
					.product(purifiedWater).component(cap).quantity(1).build());
				productComponentRepository.save(ProductComponent.builder()
					.product(purifiedWater).component(seal).quantity(1).build());
			}
			if (alkalineWater != null && cap != null && seal != null) {
				productComponentRepository.save(ProductComponent.builder()
					.product(alkalineWater).component(cap).quantity(1).build());
				productComponentRepository.save(ProductComponent.builder()
					.product(alkalineWater).component(seal).quantity(1).build());
			}
			log.info("Seeded 4 default product components (BOM)");
		}

		if (orderRepository.count() == 0) {
			final Customer juan = customerRepository.findByNameIgnoreCase("Juan Dela Cruz").orElse(null);
			final Customer maria = customerRepository.findByNameIgnoreCase("Maria Santos").orElse(null);
			final Product purifiedWater = productRepository.findByNameIgnoreCase("Purified Water").orElse(null);
			final Product jug = productRepository.findByNameIgnoreCase("Water Jug 5 Gal").orElse(null);

			if (juan != null && purifiedWater != null && jug != null) {
				final Order order1 = orderRepository.save(Order.builder()
					.customer(juan).orderType(OrderType.walk_in).status(OrderStatus.queued)
					.paymentMethod(PaymentMethod.cash).totalAmount(new BigDecimal("300"))
					.amountPaid(new BigDecimal("300")).deliveryFee(BigDecimal.ZERO).build());
				orderItemRepository.save(OrderItem.builder()
					.order(order1).product(purifiedWater).quantity(10)
					.unitPrice(new BigDecimal("25")).subtotal(new BigDecimal("250")).build());
				orderItemRepository.save(OrderItem.builder()
					.order(order1).product(jug).quantity(1)
					.unitPrice(new BigDecimal("50")).subtotal(new BigDecimal("50")).build());
			}

			if (maria != null && purifiedWater != null) {
				final Order order2 = orderRepository.save(Order.builder()
					.customer(maria).orderType(OrderType.delivery).status(OrderStatus.processing)
					.paymentMethod(PaymentMethod.credit).totalAmount(new BigDecimal("125"))
					.amountPaid(BigDecimal.ZERO).deliveryFee(new BigDecimal("20"))
					.deliveryAddress("123 Water St").build());
				orderItemRepository.save(OrderItem.builder()
					.order(order2).product(purifiedWater).quantity(5)
					.unitPrice(new BigDecimal("25")).subtotal(new BigDecimal("125")).build());
			}

			log.info("Seeded 2 default orders");
		}

		if (meterReadingRepository.count() == 0) {
			meterReadingRepository.save(MeterReading.builder()
				.readingDate(java.time.LocalDate.now().minusDays(5))
				.meterValue(new BigDecimal("0"))
				.notes("Start of week").build());
			meterReadingRepository.save(MeterReading.builder()
				.readingDate(java.time.LocalDate.now().minusDays(4))
				.meterValue(new BigDecimal("15"))
				.notes(null).build());
			meterReadingRepository.save(MeterReading.builder()
				.readingDate(java.time.LocalDate.now().minusDays(3))
				.meterValue(new BigDecimal("25"))
				.notes(null).build());
			meterReadingRepository.save(MeterReading.builder()
				.readingDate(java.time.LocalDate.now().minusDays(2))
				.meterValue(new BigDecimal("35"))
				.notes(null).build());
			meterReadingRepository.save(MeterReading.builder()
				.readingDate(java.time.LocalDate.now().minusDays(1))
				.meterValue(new BigDecimal("45"))
				.notes("End of previous day").build());
			meterReadingRepository.save(MeterReading.builder()
				.readingDate(java.time.LocalDate.now())
				.meterValue(new BigDecimal("54"))
				.notes("End of shift").build());
			log.info("Seeded 6 default meter readings");
		}
	}
}
