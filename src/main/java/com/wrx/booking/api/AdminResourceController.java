package com.wrx.booking.api;

import com.wrx.booking.domain.ResourceMachine;
import com.wrx.booking.service.ResourceMachineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/machines")
public class AdminResourceController {
    private final ResourceMachineService service;

    public AdminResourceController(ResourceMachineService service) { this.service = service; }

    @GetMapping
    public List<ResourceMachine> list() { return service.findAll(); }

    @PatchMapping("/{id}/status")
    public ResourceMachine updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    public record StatusRequest(@NotBlank String status) {}
}
