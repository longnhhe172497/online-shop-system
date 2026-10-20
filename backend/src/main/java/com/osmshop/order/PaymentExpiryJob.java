package com.osmshop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Gives back stock and vouchers held by QR codes nobody paid. Disable with app.payment-expiry.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.payment-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentExpiryJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentExpiryJob.class);
    private final PaymentService payments;

    public PaymentExpiryJob(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${app.payment-expiry.interval-ms:30000}", initialDelay = 10000)
    void expireOverdueQrCodes() {
        int expired = payments.expireDue();
        if (expired > 0) LOGGER.info("Expired {} simulated QR payment attempt(s)", expired);
    }
}
