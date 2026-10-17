package com.buildmybundle.payments;

import java.math.BigDecimal;

public record PaymentReceipt(String reference, String method, String status, BigDecimal amount) {
}
