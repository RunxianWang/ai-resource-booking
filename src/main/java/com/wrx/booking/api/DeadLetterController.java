package com.wrx.booking.api;

import com.wrx.booking.domain.DeadLetterLog;
import com.wrx.booking.service.DeadLetterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dead-letters")
public class DeadLetterController {

    private final DeadLetterService service;

    public DeadLetterController(DeadLetterService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeadLetterLog> list(@RequestParam(defaultValue = "50") int limit) {
        return service.findAll(limit);
    }

    @GetMapping("/{id}")
    public DeadLetterLog get(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/replay")
    public DeadLetterLog replay(@PathVariable long id) {
        return service.replay(id);
    }

    @PostMapping("/{id}/ignore")
    public DeadLetterLog ignore(@PathVariable long id) {
        return service.ignore(id);
    }
}
