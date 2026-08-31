package com.wrx.booking.service;

import com.wrx.booking.domain.ResourceMachine;
import com.wrx.booking.repository.ResourceMachineRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResourceMachineService {
    private final ResourceMachineRepository repository;

    public ResourceMachineService(ResourceMachineRepository repository) { this.repository = repository; }

    public List<ResourceMachine> findAll() { return repository.findAll(); }

    public ResourceMachine updateStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("status must be ACTIVE or INACTIVE");
        }
        if (repository.updateStatus(id, status) == 0) {
            throw new IllegalArgumentException("machine not found: " + id);
        }
        return repository.findAll().stream().filter(machine -> machine.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("machine not found: " + id));
    }
}
