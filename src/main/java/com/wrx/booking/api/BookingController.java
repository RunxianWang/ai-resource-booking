package com.wrx.booking.api;

import com.wrx.booking.api.dto.BookingRequest;
import com.wrx.booking.api.dto.BookingCancelResponse;
import com.wrx.booking.api.dto.BookingResponse;
import com.wrx.booking.api.dto.SlotBookingResponse;
import com.wrx.booking.api.dto.UserBookingResponse;
import com.wrx.booking.service.BookingService;
import com.wrx.booking.support.DemoUserContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;
    private final DemoUserContext demoUserContext;

    public BookingController(BookingService bookingService, DemoUserContext demoUserContext) {
        this.bookingService = bookingService;
        this.demoUserContext = demoUserContext;
    }

    /**
     * 提交资源预约请求。
     */
    @PostMapping
    public BookingResponse book(@Valid @RequestBody BookingRequest request) {
        Long demoUserId = demoUserContext.userId();
        if (request.userId() != null && !demoUserId.equals(request.userId())) {
            log.warn(
                    "event=booking.request.user_ignored requestUserId={} demoUserId={} slotId={}",
                    request.userId(),
                    demoUserId,
                    request.slotId()
            );
        }
        return bookingService.book(demoUserId, request.slotId());
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingCancelResponse cancel(@PathVariable Long bookingId) {
        return bookingService.cancel(bookingId, demoUserContext.userId());
    }

    @GetMapping("/my")
    public List<UserBookingResponse> listMyBookings() {
        return bookingService.listUserBookings(demoUserContext.userId());
    }

    @GetMapping("/users/{userId}")
    public List<UserBookingResponse> listUserBookings(@PathVariable Long userId) {
        Long demoUserId = demoUserContext.userId();
        if (!demoUserId.equals(userId)) {
            log.warn(
                    "event=booking.query.user_ignored requestUserId={} demoUserId={}",
                    userId,
                    demoUserId
            );
        }
        return bookingService.listUserBookings(demoUserId);
    }

    @GetMapping("/slots/{slotId}")
    public List<SlotBookingResponse> listSlotBookings(@PathVariable Long slotId) {
        return bookingService.listSlotBookings(slotId);
    }
}
