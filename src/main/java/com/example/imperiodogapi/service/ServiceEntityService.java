package com.example.imperiodogapi.service;

import com.example.imperiodogapi.dto.ServiceDTO;
import com.example.imperiodogapi.entities.ServiceEntity;
import com.example.imperiodogapi.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ServiceEntityService {

    private final ServiceRepository serviceRepository;

    public ServiceEntityService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional
    public ServiceDTO create(ServiceDTO request) {
        ServiceEntity service = ServiceEntity.builder().name(request.name().trim())
                .description(request.description()).price(request.price()).build();
        return toDto(serviceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> findAll() {
        return serviceRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ServiceDTO findById(Long id) {
        return toDto(getService(id));
    }

    @Transactional
    public ServiceDTO update(Long id, ServiceDTO request) {
        ServiceEntity service = getService(id);
        service.setName(request.name().trim());
        service.setDescription(request.description());
        service.setPrice(request.price());
        return toDto(serviceRepository.save(service));
    }

    @Transactional
    public void delete(Long id) {
        serviceRepository.delete(getService(id));
    }

    private ServiceEntity getService(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    private ServiceDTO toDto(ServiceEntity service) {
        return new ServiceDTO(service.getId(), service.getName(), service.getDescription(), service.getPrice());
    }
}
