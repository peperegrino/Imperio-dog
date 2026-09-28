package com.example.imperiodogapi.service;

import com.example.imperiodogapi.entities.Charge;
import tools.jackson.databind.JsonNode;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class MercadoPagoService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final String accessToken;
    private final String notificationUrl;
    private final RestClient restClient;
    private final PaymentClient paymentClient;

    public MercadoPagoService(@Value("${mercadopago.access-token:}") String accessToken,
                              @Value("${mercadopago.notification-url:}") String notificationUrl) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("MERCADOPAGO_ACCESS_TOKEN must be configured");
        }
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
        MercadoPagoConfig.setAccessToken(accessToken);
        this.paymentClient = new PaymentClient();
        this.restClient = RestClient.builder().baseUrl("https://api.mercadopago.com").build();
    }

    public MercadoPagoPaymentResult createBoleto(Charge charge) {
        var user = charge.getCustomer().getUser();
        requireBoletoAddress(user.getZipCode(), user.getStreetName(), user.getStreetNumber(),
                user.getNeighborhood(), user.getCity(), user.getFederalUnit());

        String[] names = user.getName().trim().split("\\s+", 2);
        Map<String, Object> address = Map.of(
                "zip_code", user.getZipCode(),
                "street_name", user.getStreetName(),
                "street_number", user.getStreetNumber(),
                "neighborhood", user.getNeighborhood(),
                "city", user.getCity(),
                "federal_unit", user.getFederalUnit());
        Map<String, Object> payer = Map.of(
                "email", user.getEmail(),
                "first_name", names[0],
                "last_name", names.length > 1 ? names[1] : names[0],
                "identification", Map.of("type", "CPF", "number", user.getCpf()),
                "address", address);
        var paymentRequest = new java.util.HashMap<String, Object>();
        paymentRequest.put("transaction_amount", charge.getAmount());
        paymentRequest.put("currency_id", "BRL");
        paymentRequest.put("description", charge.getService().getName());
        paymentRequest.put("payment_method_id", "bolbradesco");
        paymentRequest.put("external_reference", charge.getId().toString());
        paymentRequest.put("date_of_expiration", charge.getDueDate().atTime(23, 59)
                .atZone(SAO_PAULO).toOffsetDateTime().toString());
        paymentRequest.put("payer", payer);
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            paymentRequest.put("notification_url", notificationUrl);
        }

        try {
            JsonNode response = restClient.post().uri("/v1/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", "imperio-dog-charge-" + charge.getId())
                    .body(paymentRequest)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("id")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Mercado Pago returned an invalid payment response");
            }
            String ticketUrl = response.path("transaction_details").path("external_resource_url").asText(null);
            if (ticketUrl == null || ticketUrl.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Mercado Pago did not return the boleto ticket URL");
            }
            return new MercadoPagoPaymentResult(response.path("id").asText(), ticketUrl,
                    response.path("status").asText("pending"));
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create boleto with Mercado Pago", exception);
        }
    }

    public String getPaymentStatus(Long id) {
        try {
            Payment payment = paymentClient.get(id);
            return payment.getStatus();
        } catch (MPException | MPApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not retrieve Mercado Pago payment status", exception);
        }
    }

    private void requireBoletoAddress(String zipCode, String streetName, String streetNumber,
                                      String neighborhood, String city, String federalUnit) {
        if (zipCode == null || zipCode.isBlank() || streetName == null || streetName.isBlank()
                || streetNumber == null || streetNumber.isBlank() || neighborhood == null
                || neighborhood.isBlank() || city == null || city.isBlank()
                || federalUnit == null || federalUnit.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Complete customer address is required to create a boleto");
        }
    }

    public record MercadoPagoPaymentResult(String id, String ticketUrl, String status) {
    }
}
