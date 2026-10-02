package com.example.imperiodogapi.controller;

import com.example.imperiodogapi.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/webhooks/mercadopago", "/api/webhooks/mercadopago"})
public class MercadoPagoWebhookController {

    private final WebhookService webhookService;

    public MercadoPagoWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receiveNotification(
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId) {
        if (dataId == null || !dataId.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or invalid data.id");
        }
        if (!webhookService.validateSignature(signature, requestId, dataId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Mercado Pago signature");
        }
        webhookService.processNotification(Long.valueOf(dataId));
        return ResponseEntity.ok().build();
    }
}
