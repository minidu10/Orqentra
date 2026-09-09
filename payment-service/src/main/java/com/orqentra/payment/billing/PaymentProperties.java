package com.orqentra.payment.billing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param declineAbove orders with a total strictly greater than this are declined.
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(BigDecimal declineAbove) {}
