package BookingService.BookingService.controller;

import BookingService.BookingService.dto.request.BookingRequest;
import BookingService.BookingService.dto.response.BookingResponse;
import BookingService.BookingService.service.BookingService;
import BookingService.BookingService.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        List<BookingResponse> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'USER', 'SPECIALIST')")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        Optional<BookingResponse> booking = bookingService.getBookingById(id);
        return booking.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/confirmed-or-in-progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<List<BookingResponse>> getConfirmedOrInProgressBookings() {
        List<BookingResponse> bookings = bookingService.getConfirmedOrInProgressBookings();
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<BookingResponse>> getBookingsForCurrentUser() {
        List<BookingResponse> bookings = bookingService.getBookingsForCurrentUser();
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/specialist-bookings")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ResponseEntity<List<BookingResponse>> getBookingsForCurrentSpecialist() {
        List<BookingResponse> bookings = bookingService.getBookingsForCurrentSpecialist();
        return ResponseEntity.ok(bookings);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        BookingResponse bookingResponse = bookingService.createBooking(request);
        return ResponseEntity.ok(bookingResponse);
    }

    @PostMapping("/guest")
    public ResponseEntity<BookingResponse> createGuestBooking(@Valid @RequestBody BookingRequest request) {
        BookingResponse bookingResponse = bookingService.createGuestBooking(request);
        return ResponseEntity.ok(bookingResponse);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponse> updateBooking(@PathVariable Long id, @Valid @RequestBody BookingRequest request) {
        BookingResponse updatedBooking = bookingService.updateBooking(id, request);
        return ResponseEntity.ok(updatedBooking);
    }

    @PutMapping("/cancel/{bookingId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'STAFF')")
    public ResponseEntity<BookingResponse> cancelBookingByUser(@PathVariable Long bookingId) {
        BookingResponse cancelledBooking = bookingService.cancelBookingByUser(bookingId);
        return ResponseEntity.ok(cancelledBooking);
    }

    @PutMapping("/confirm/{bookingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BookingResponse> updateBookingStatusByStaff(@PathVariable Long bookingId) {
        BookingResponse updatedBooking = bookingService.updateBookingStatusByStaff(bookingId);
        return ResponseEntity.ok(updatedBooking);
    }

    @PutMapping("/check-in/{bookingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BookingResponse> checkInBooking(@PathVariable Long bookingId) {
        BookingResponse checkedInBooking = bookingService.checkInBooking(bookingId);
        return ResponseEntity.ok(checkedInBooking);
    }

    @PutMapping("/check-out/{bookingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BookingResponse> checkOutBooking(@PathVariable Long bookingId) {
        BookingResponse checkedOutBooking = bookingService.checkOutBooking(bookingId);
        return ResponseEntity.ok(checkedOutBooking);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBooking(@PathVariable Long id) {
        bookingService.deleteBooking(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/revenue/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BigDecimal> getDailyRevenue(@RequestParam(required = false) LocalDate date) {
        BigDecimal revenue = bookingService.getDailyRevenue(date);
        return ResponseEntity.ok(revenue);
    }

    @GetMapping("/revenue/weekly")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BigDecimal> getWeeklyRevenue(@RequestParam(required = false) LocalDate dateInWeek) {
        BigDecimal revenue = bookingService.getWeeklyRevenue(dateInWeek);
        return ResponseEntity.ok(revenue);
    }

    @GetMapping("/revenue/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BigDecimal> getMonthlyRevenue(
            @RequestParam int year,
            @RequestParam int month) {
        BigDecimal revenue = bookingService.getMonthlyRevenue(year, month);
        return ResponseEntity.ok(revenue);
    }

    @GetMapping("/revenue/range")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<BigDecimal> getRevenueInRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        BigDecimal revenue = bookingService.getRevenueInRange(startDate, endDate);
        return ResponseEntity.ok(revenue);
    }
}