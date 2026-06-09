package com.wrx.booking.api;

import com.wrx.booking.api.dto.SlotListResponse;
import com.wrx.booking.api.dto.SlotResponse;
import com.wrx.booking.service.SlotService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public List<SlotListResponse> listSlots() {
        return slotService.listSlots();
    }

    /**
     * 查询指定资源时段库存。
     */
    @GetMapping("/{slotId}")
    public SlotResponse getSlot(@PathVariable Long slotId) {
        return slotService.querySlot(slotId);
    }

    /**
     * 将 MySQL 库存加载到 Redis，供高并发预约链路使用。
     */
    @PostMapping("/{slotId}/warmup")
    public Map<String, Object> warmup(@PathVariable Long slotId) {
        slotService.warmupSlot(slotId);
        return Map.of(
                "code", "SUCCESS",
                "message", "Redis 库存预热完成",
                "slotId", slotId
        );
    }
}
