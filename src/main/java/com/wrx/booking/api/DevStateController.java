package com.wrx.booking.api;

import com.wrx.booking.api.dto.DevStateResponse;
import com.wrx.booking.service.DevStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
public class DevStateController {

    private final DevStateService devStateService;

    public DevStateController(DevStateService devStateService) {
        this.devStateService = devStateService;
    }

    @GetMapping("/state/{slotId}")
    public DevStateResponse state(@PathVariable Long slotId) {
        return devStateService.getState(slotId);
    }
}
