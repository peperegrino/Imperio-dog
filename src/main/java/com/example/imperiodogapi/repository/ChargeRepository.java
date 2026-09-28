package com.example.imperiodogapi.repository;

import com.example.imperiodogapi.entities.Charge;
import com.example.imperiodogapi.entities.enums.ChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeRepository extends JpaRepository<Charge, Long> {

    List<Charge> findByCustomerId(Long customerId);

    List<Charge> findByStatus(ChargeStatus status);

    List<Charge> findByMercadoPagoId(String mercadoPagoId);
}
