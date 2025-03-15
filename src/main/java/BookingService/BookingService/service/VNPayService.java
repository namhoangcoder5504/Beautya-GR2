package BookingService.BookingService.service;

import BookingService.BookingService.dto.request.ApiResponse;
import BookingService.BookingService.dto.response.PaymentResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.util.List;

public interface VNPayService {
    String createPayment(int total, String orderInfo, String urlReturn, HttpServletRequest request);
    int orderReturn(HttpServletRequest request);
    ApiResponse<String> getPaymentInfo(HttpServletRequest request, HttpServletResponse response);
    List<PaymentResponse> getAllPayments();
    ApiResponse<String> processCashPayment(Long bookingId, BigDecimal amount);
}