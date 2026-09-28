package com.example.imperiodogapi.service;

import com.example.imperiodogapi.entities.Charge;
import com.example.imperiodogapi.entities.enums.ChargeStatus;
import com.example.imperiodogapi.repository.ChargeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookService.class);
    private static final long MAX_SIGNATURE_AGE_SECONDS = 5 * 60;

    private final String webhookSecret;
    private final MercadoPagoService mercadoPagoService;
    private final ChargeRepository chargeRepository;
    private final TransactionTemplate transactionTemplate;

    public WebhookService(@Value("${mercadopago.webhook-secret:}") String webhookSecret,
                          MercadoPagoService mercadoPagoService,
                          ChargeRepository chargeRepository,
                          org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.webhookSecret = webhookSecret;
        this.mercadoPagoService = mercadoPagoService;
        this.chargeRepository = chargeRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    void validateConfiguration() {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("MERCADOPAGO_WEBHOOK_SECRET must be configured");
        }
    }

    public boolean validateSignature(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null || xRequestId == null || xRequestId.isBlank()
                || dataId == null || dataId.isBlank()) {
            return false;
        }
        String timestamp = null;
        String suppliedHash = null;
        for (String part : xSignature.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if (pair[0].trim().equals("ts")) timestamp = pair[1].trim();
            if (pair[0].trim().equals("v1")) suppliedHash = pair[1].trim();
        }
        if (timestamp == null || suppliedHash == null || !timestamp.matches("\\d{1,13}")) {
            return false;
        }
        try {
            long timestampSeconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - timestampSeconds) > MAX_SIGNATURE_AGE_SECONDS) {
                return false;
            }
            String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + timestamp + ";";
            byte[] expected = hmacSha256(manifest);
            byte[] supplied = HexFormat.of().parseHex(suppliedHash);
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (Exception exception) {
            LOGGER.error("Unable to validate Mercado Pago webhook signature", exception);
            return false;
        }
    }

    @Async("webhookTaskExecutor")
    public void processNotification(Long dataId) {
        try {
            transactionTemplate.executeWithoutResult(status -> synchronizeCharge(dataId));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not process Mercado Pago notification for payment {}", dataId, exception);
        }
    }

    private void synchronizeCharge(Long dataId) {
        String paymentStatus = mercadoPagoService.getPaymentStatus(dataId);
        ChargeStatus newStatus = mapStatus(paymentStatus);
        if (newStatus == null) {
            LOGGER.info("Ignoring unsupported Mercado Pago payment status '{}' for {}", paymentStatus, dataId);
            return;
        }
        List<Charge> matchingCharges = chargeRepository.findByMercadoPagoId(dataId.toString());
        if (matchingCharges.size() != 1) {
            LOGGER.warn("Expected one charge for Mercado Pago payment {}, found {}", dataId, matchingCharges.size());
            return;
        }
        Charge charge = matchingCharges.getFirst();
        if (charge.getStatus() == ChargeStatus.PAID || charge.getStatus() == ChargeStatus.CANCELED) {
            return;
        }
        charge.setStatus(newStatus);
        if (newStatus == ChargeStatus.PAID && charge.getPaymentDate() == null) {
            charge.setPaymentDate(java.time.LocalDateTime.now());
        }
        chargeRepository.save(charge);
    }

    private ChargeStatus mapStatus(String status) {
        if (status == null) return null;
        return switch (status.toLowerCase(java.util.Locale.ROOT)) {
            case "approved" -> ChargeStatus.PAID;
            case "in_process", "pending" -> ChargeStatus.PAYMENT_IN_PROCESS;
            case "cancelled", "canceled", "rejected" -> ChargeStatus.CANCELED;
            default -> null;
        };
    }

    private byte[] hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
