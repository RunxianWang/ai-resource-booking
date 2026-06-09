package com.wrx.booking.api;

import com.wrx.booking.api.dto.VerifyResponse;
import com.wrx.booking.service.SlotService;
import com.wrx.booking.service.VerifyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 开发测试辅助接口。
 * 仅用于本地开发阶段重置和校验测试数据。
 */
@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final SlotService slotService;
    private final VerifyService verifyService;

    public DevController(
            SlotService slotService,
            VerifyService verifyService
    ) {
        this.slotService = slotService;
        this.verifyService = verifyService;
    }

    /**
     * 重置指定资源时段的预约数据和库存。
     */
    @PostMapping("/reset/{slotId}")
    public Map<String, Object> reset(@PathVariable Long slotId) {
        slotService.resetSlotForDev(slotId);
        return Map.of(
                "code", "SUCCESS",
                "message", "测试数据已重置",
                "slotId", slotId
        );
    }

    /**
     * 校验指定资源时段的库存、预约记录、消息日志、消费日志是否一致。
     */
    @GetMapping("/verify/{slotId}")
    public VerifyResponse verify(@PathVariable Long slotId) {
        return verifyService.verify(slotId);
    }
}