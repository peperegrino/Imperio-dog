package com.example.imperiodogapi.service;

import com.example.imperiodogapi.dto.PetDTO;
import com.example.imperiodogapi.entities.Customer;
import com.example.imperiodogapi.entities.Pet;
import com.example.imperiodogapi.repository.CustomerRepository;
import com.example.imperiodogapi.repository.PetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PetService {

    private final PetRepository petRepository;
    private final CustomerRepository customerRepository;

    public PetService(PetRepository petRepository, CustomerRepository customerRepository) {
        this.petRepository = petRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public PetDTO create(PetDTO request) {
        Customer customer = getCustomer(request.customerId());
        Pet pet = Pet.builder().name(request.name().trim()).species(request.species())
                .breed(request.breed()).customer(customer).build();
        return toDto(petRepository.save(pet));
    }

    @Transactional(readOnly = true)
    public List<PetDTO> findAll() {
        return petRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PetDTO findById(Long id) {
        return toDto(getPet(id));
    }

    @Transactional
    public PetDTO update(Long id, PetDTO request) {
        Pet pet = getPet(id);
        pet.setName(request.name().trim());
        pet.setSpecies(request.species());
        pet.setBreed(request.breed());
        pet.setCustomer(getCustomer(request.customerId()));
        return toDto(petRepository.save(pet));
    }

    @Transactional
    public void delete(Long id) {
        petRepository.delete(getPet(id));
    }

    private Pet getPet(Long id) {
        return petRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private PetDTO toDto(Pet pet) {
        return new PetDTO(pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getCustomer().getId());
    }
}
