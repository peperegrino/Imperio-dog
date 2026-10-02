package com.example.imperiodogapi.controller;

import com.example.imperiodogapi.dto.ChargeResponseDTO;
import com.example.imperiodogapi.service.ChargeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/charges", "/api/charges"})
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @GetMapping("/my-charges")
    public List<ChargeResponseDTO> myCharges(@AuthenticationPrincipal UserDetails principal) {
        return chargeService.findMyCharges(principal.getUsername());
    }

    @GetMapping("/{id}")
    public ChargeResponseDTO getCharge(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails principal) {
        boolean admin = AuthorityUtils.authorityListToSet(principal.getAuthorities()).contains("ROLE_ADMIN");
        return chargeService.findByIdForUser(id, principal.getUsername(), admin);
    }
}
