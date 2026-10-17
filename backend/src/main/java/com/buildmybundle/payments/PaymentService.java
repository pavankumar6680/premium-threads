package com.buildmybundle.payments;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Optional;

import com.buildmybundle.payments.domain.Payment;
import com.buildmybundle.payments.domain.PaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated payment provider: every charge is approved and recorded with a reference, and refunds flip
 * the status. No card data is ever collected or stored. Swapping in a real provider means implementing
 * the same two operations against its API.
 */
@Service
@Transactional
public class PaymentService {

	public static final String SIMULATED_METHOD = "SIMULATED_CARD";

	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PaymentRepository payments;

	public PaymentService(PaymentRepository payments) {
		this.payments = payments;
	}

	public PaymentReceipt charge(Long orderId, BigDecimal amount) {
		Payment payment = payments.save(new Payment(orderId, amount, SIMULATED_METHOD, "SIM-" + randomCode(12)));
		return receipt(payment);
	}

	public void refund(Long orderId) {
		payments.findByOrderId(orderId).ifPresent(Payment::refund);
	}

	@Transactional(readOnly = true)
	public Optional<PaymentReceipt> findByOrder(Long orderId) {
		return payments.findByOrderId(orderId).map(PaymentService::receipt);
	}

	private static PaymentReceipt receipt(Payment p) {
		return new PaymentReceipt(p.getReference(), p.getMethod(), p.getStatus().name(), p.getAmount());
	}

	static String randomCode(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
