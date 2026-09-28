package com.example.imperiodogapi.service;

import com.example.imperiodogapi.dto.ChargeRequestDTO;
import com.example.imperiodogapi.dto.ChargeResponseDTO;
import com.example.imperiodogapi.entities.Charge;
import com.example.imperiodogapi.entities.enums.ChargeStatus;
import com.example.imperiodogapi.repository.ChargeRepository;
import com.example.imperiodogapi.repository.CustomerRepository;
import com.example.imperiodogapi.repository.PetRepository;
import com.example.imperiodogapi.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final CustomerRepository customerRepository;
    private final PetRepository petRepository;
    private final ServiceRepository serviceRepository;
    private final MercadoPagoService mercadoPagoService;

    public ChargeService(ChargeRepository chargeRepository, CustomerRepository customerRepository,
                         PetRepository petRepository, ServiceRepository serviceRepository,
                         MercadoPagoService mercadoPagoService) {
        this.chargeRepository = chargeRepository;
        this.customerRepository = customerRepository;
        this.petRepository = petRepository;
        this.serviceRepository = serviceRepository;
        this.mercadoPagoService = mercadoPagoService;
    }

    @Transactional
    public ChargeResponseDTO createCharge(ChargeRequestDTO request) {
        if (request.dueDate().isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Boleto due date must be at least three days in the future");
        }
        var customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        var pet = petRepository.findById(request.petId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));
        var service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        if (!pet.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet does not belong to this customer");
        }

        Charge charge = Charge.builder()
                .customer(customer)
                .pet(pet)
                .service(service)
                .amount(service.getPrice())
                .serviceDate(request.serviceDate())
                .dueDate(request.dueDate())
                .status(ChargeStatus.PENDING)
                .build();
        charge = chargeRepository.saveAndFlush(charge);

        MercadoPagoService.MercadoPagoPaymentResult payment = mercadoPagoService.createBoleto(charge);
        charge.setMercadoPagoId(payment.id());
        charge.setTicketUrl(payment.ticketUrl());
        return toResponse(chargeRepository.save(charge));
    }

    @Transactional(readOnly = true)
    public List<ChargeResponseDTO> findMyCharges(String email) {
        var customer = customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return chargeRepository.findByCustomerId(customer.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChargeResponseDTO> findAll(ChargeStatus status) {
        List<Charge> charges = status == null ? chargeRepository.findAll() : chargeRepository.findByStatus(status);
        return charges.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChargeResponseDTO findByIdForUser(Long id, String email, boolean admin) {
        Charge charge = chargeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found"));
        if (!admin && !charge.getCustomer().getUser().getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot view this charge");
        }
        return toResponse(charge);
    }

    private ChargeResponseDTO toResponse(Charge charge) {
        return new ChargeResponseDTO(charge.getId(), charge.getCustomer().getUser().getName(),
                charge.getPet().getName(), charge.getService().getName(), charge.getAmount(),
                charge.getServiceDate(), charge.getDueDate(), charge.getPaymentDate(),
                charge.getStatus(), charge.getTicketUrl());
    }
}
