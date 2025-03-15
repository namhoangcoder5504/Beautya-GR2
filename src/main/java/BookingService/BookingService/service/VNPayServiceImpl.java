package BookingService.BookingService.service;

import BookingService.BookingService.configuration.VnPayConfiguration;
import BookingService.BookingService.dto.request.ApiResponse;
import BookingService.BookingService.dto.response.PaymentResponse;
import BookingService.BookingService.entity.Booking;
import BookingService.BookingService.entity.Payment;
import BookingService.BookingService.enums.BookingStatus;
import BookingService.BookingService.enums.PaymentStatus;
import BookingService.BookingService.exception.AppException;
import BookingService.BookingService.exception.ErrorCode;
import BookingService.BookingService.repository.BookingRepository;
import BookingService.BookingService.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VNPayServiceImpl implements VNPayService {
    BookingRepository bookingRepository;
    PaymentRepository paymentRepository;
    EmailService emailService;

    private static final String DEFAULT_SUCCESS_REDIRECT_URL = "https://yourfrontenddomain.com/mybooking";
    private static final String DEFAULT_FAILED_REDIRECT_URL = "https://yourfrontenddomain.com/mybooking";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    @PreAuthorize("hasRole('USER')")
    public String createPayment(int total, String orderInfo, String urlReturn, HttpServletRequest request) {
        Long bookingId = extractBookingIdFromOrderInfo(orderInfo);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_EXISTED));

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.BOOKING_NOT_CHECKED_IN);
        }

        if (total != booking.getTotalPrice().intValue()) {
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        Payment existingPayment = paymentRepository.findByBookingId(bookingId)
                .orElse(null);

        if (existingPayment != null && PaymentStatus.SUCCESS.equals(existingPayment.getStatus())) {
            throw new AppException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }

        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = VnPayConfiguration.getRandomNumber(8);
        String vnp_IpAddr = VnPayConfiguration.getIpAddress(request);
        String vnp_TmnCode = VnPayConfiguration.vnp_TmnCode;
        String orderType = "order-type";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(total * 100));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", orderType);

        String locate = "vn";
        vnp_Params.put("vnp_Locale", locate);

        vnp_Params.put("vnp_ReturnUrl", urlReturn + VnPayConfiguration.vnp_ReturnURL);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String vnp_CreateDate = now.format(formatter);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        ZonedDateTime expireDate = now.plusMinutes(30);
        String vnp_ExpireDate = expireDate.format(formatter);
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                try {
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = VnPayConfiguration.hmacSHA512(VnPayConfiguration.vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

        if (existingPayment == null) {
            existingPayment = Payment.builder()
                    .booking(booking)
                    .amount(BigDecimal.valueOf(total))
                    .paymentMethod("VNPAY")
                    .transactionId(vnp_TxnRef)
                    .status(PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(existingPayment);
        } else {
            existingPayment.setTransactionId(vnp_TxnRef);
            existingPayment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(existingPayment);
        }

        log.info("vnp_CreateDate: {}", vnp_CreateDate);
        log.info("vnp_ExpireDate: {}", vnp_ExpireDate);
        log.info("vnp_ReturnUrl: {}", vnp_Params.get("vnp_ReturnUrl"));
        log.info("Payment URL: {}", VnPayConfiguration.vnp_PayUrl + "?" + queryUrl);

        return VnPayConfiguration.vnp_PayUrl + "?" + queryUrl;
    }

    @Override
    public int orderReturn(HttpServletRequest request) {
        Map fields = new HashMap();
        for (Enumeration params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = null;
            String fieldValue = null;
            try {
                fieldName = URLEncoder.encode((String) params.nextElement(), StandardCharsets.US_ASCII.toString());
                fieldValue = URLEncoder.encode(request.getParameter(fieldName), StandardCharsets.US_ASCII.toString());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        if (fields.containsKey("vnp_SecureHashType")) {
            fields.remove("vnp_SecureHashType");
        }
        if (fields.containsKey("vnp_SecureHash")) {
            fields.remove("vnp_SecureHash");
        }
        String signValue = VnPayConfiguration.hashAllFields(fields);
        if (signValue.equals(vnp_SecureHash)) {
            if ("00".equals(request.getParameter("vnp_TransactionStatus"))) {
                return 1;
            } else {
                return 0;
            }
        } else {
            return -1;
        }
    }

    @Override
    @Transactional
    public ApiResponse<String> getPaymentInfo(HttpServletRequest request, HttpServletResponse response) {
        int paymentStatus = orderReturn(request);
        String orderInfo = request.getParameter("vnp_OrderInfo");
        String transactionId = request.getParameter("vnp_TransactionNo");
        BigDecimal amount = new BigDecimal(request.getParameter("vnp_Amount")).divide(new BigDecimal(100));
        String redirectUrlParam = request.getParameter("redirectUrl");

        Long bookingId = extractBookingIdFromOrderInfo(orderInfo);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        Payment existingPayment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String token = authentication != null ? (String) authentication.getCredentials() : null;
        if (token == null) {
            token = "fallback-token";
        }

        if (existingPayment.getStatus() == PaymentStatus.SUCCESS) {
            try {
                String redirectUrl = redirectUrlParam != null && !redirectUrlParam.isEmpty() ? redirectUrlParam : DEFAULT_SUCCESS_REDIRECT_URL;
                redirectUrl += "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.toString()) + "&status=success";
                response.sendRedirect(redirectUrl);
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Redirect failed", e);
            }
        }

        if (paymentStatus == 1) {
            existingPayment.setStatus(PaymentStatus.SUCCESS);
            existingPayment.setTransactionId(transactionId);
            existingPayment.setAmount(amount);

            ZonedDateTime paymentTime = ZonedDateTime.now(VIETNAM_ZONE);
            existingPayment.setPaymentTime(paymentTime.toLocalDateTime());
            paymentRepository.save(existingPayment);

            booking.setPaymentStatus(PaymentStatus.SUCCESS);
            booking.setPayment(existingPayment);
            bookingRepository.save(booking);

            String customerEmail = booking.getCustomer() != null ? booking.getCustomer().getEmail() : null;
            String customerName = booking.getCustomer() != null ? booking.getCustomer().getName() : "Khách hàng";
            String specialistName = booking.getSpecialist() != null ? booking.getSpecialist().getName() : "Chuyên viên";
            String transactionNo = transactionId != null ? transactionId : "N/A";
            String transactionTime = request.getParameter("vnp_PayDate");
            if (transactionTime != null) {
                try {
                    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                    LocalDateTime payDate = LocalDateTime.parse(transactionTime, inputFormatter);
                    ZonedDateTime zonedPayDate = payDate.atZone(VIETNAM_ZONE);
                    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                    transactionTime = zonedPayDate.format(outputFormatter);
                } catch (Exception e) {
                    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                    transactionTime = paymentTime.format(outputFormatter);
                }
            } else {
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                transactionTime = paymentTime.format(outputFormatter);
            }
            BigDecimal totalAmount = amount != null ? amount : BigDecimal.ZERO;

            if (customerEmail != null && !customerEmail.isEmpty()) {
                String subject = "Hóa đơn thanh toán thành công từ Beautya";
                String htmlBody = buildPaymentBillEmail(customerName, specialistName, transactionNo, transactionTime, totalAmount);
                try {
                    emailService.sendEmail(customerEmail, subject, htmlBody);
                } catch (Exception e) {
                    System.err.println("Failed to send payment bill email: " + e.getMessage());
                }
            }

            try {
                String redirectUrl = redirectUrlParam != null && !redirectUrlParam.isEmpty() ? redirectUrlParam : DEFAULT_SUCCESS_REDIRECT_URL;
                redirectUrl += "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.toString()) + "&status=success";
                response.sendRedirect(redirectUrl);
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Redirect failed", e);
            }
        } else if (paymentStatus == 0) {
            existingPayment.setStatus(PaymentStatus.FAILED);
            existingPayment.setTransactionId(transactionId);
            existingPayment.setAmount(amount);
            existingPayment.setPaymentTime(ZonedDateTime.now(VIETNAM_ZONE).toLocalDateTime());
            paymentRepository.save(existingPayment);

            booking.setPaymentStatus(PaymentStatus.FAILED);
            booking.setPayment(existingPayment);
            bookingRepository.save(booking);

            try {
                String redirectUrl = redirectUrlParam != null && !redirectUrlParam.isEmpty() ? redirectUrlParam : DEFAULT_FAILED_REDIRECT_URL;
                redirectUrl += "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.toString()) + "&status=failed";
                response.sendRedirect(redirectUrl);
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Redirect failed", e);
            }
        } else {
            return ApiResponse.<String>builder()
                    .code(1002)
                    .message("Error! Secure Hash is invalid!")
                    .result(null)
                    .build();
        }
    }

    @Override
    public List<PaymentResponse> getAllPayments() {
        List<Payment> payments = paymentRepository.findAll();
        return payments.stream()
                .map(payment -> {
                    PaymentResponse response = new PaymentResponse();
                    response.setPaymentId(payment.getPaymentId());
                    response.setAmount(payment.getAmount());
                    response.setPaymentMethod(payment.getPaymentMethod());
                    response.setPaymentTime(payment.getPaymentTime());
                    response.setStatus(payment.getStatus());
                    response.setTransactionId(payment.getTransactionId());
                    response.setBookingId(payment.getBooking() != null ? payment.getBooking().getBookingId() : null);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('STAFF')")
    public ApiResponse<String> processCashPayment(Long bookingId, BigDecimal amount) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_EXISTED));

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.BOOKING_NOT_CHECKED_IN);
        }

        if (amount.compareTo(booking.getTotalPrice()) != 0) {
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElse(null);

        String transactionId = "CASH_" + System.currentTimeMillis();

        if (payment == null) {
            payment = Payment.builder()
                    .booking(booking)
                    .amount(amount)
                    .paymentMethod("CASH")
                    .transactionId(transactionId)
                    .status(PaymentStatus.SUCCESS)
                    .paymentTime(ZonedDateTime.now(VIETNAM_ZONE).toLocalDateTime())
                    .build();
        } else {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                throw new AppException(ErrorCode.PAYMENT_NOT_COMPLETED);
            }
            payment.setPaymentMethod("CASH");
            payment.setTransactionId(transactionId);
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setAmount(amount);
            payment.setPaymentTime(ZonedDateTime.now(VIETNAM_ZONE).toLocalDateTime());
        }

        paymentRepository.save(payment);

        booking.setPaymentStatus(PaymentStatus.SUCCESS);
        booking.setPayment(payment);
        bookingRepository.save(booking);

        String customerEmail = booking.getCustomer() != null ? booking.getCustomer().getEmail() : null;
        if (customerEmail != null && !customerEmail.isEmpty()) {
            String subject = "Xác nhận thanh toán tiền mặt từ Beautya";
            String htmlBody = buildCashPaymentEmail(
                    booking.getCustomer().getName(),
                    booking.getSpecialist() != null ? booking.getSpecialist().getName() : "Chuyên viên",
                    transactionId,
                    amount
            );
            try {
                emailService.sendEmail(customerEmail, subject, htmlBody);
            } catch (Exception e) {
                System.err.println("Failed to send cash payment email: " + e.getMessage());
            }
        }

        return ApiResponse.<String>builder()
                .message("Cash payment processed successfully")
                .result(transactionId)
                .build();
    }

    private Long extractBookingIdFromOrderInfo(String orderInfo) {
        try {
            String[] parts = orderInfo.split("-");
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            throw new RuntimeException("Invalid orderInfo format: " + orderInfo);
        }
    }

    private String buildCashPaymentEmail(String customerName, String specialistName, String transactionId, BigDecimal totalAmount) {
        return "<!DOCTYPE html>" +
                "<html><head><style>" +
                "body { font-family: Arial, sans-serif; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; }" +
                ".header { background-color: #28a745; color: white; padding: 10px; text-align: center; }" +
                ".content { padding: 20px; background-color: white; }" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'><h2>Xác Nhận Thanh Toán Tiền Mặt</h2></div>" +
                "<div class='content'>" +
                "<p>Xin chào " + customerName + ",</p>" +
                "<p>Thanh toán tiền mặt của bạn cho chuyên viên <strong>" + specialistName + "</strong> đã được xác nhận:</p>" +
                "<p><strong>Mã giao dịch:</strong> " + transactionId + "</p>" +
                "<p><strong>Tổng số tiền:</strong> " + totalAmount + " VNĐ</p>" +
                "</div></div></body></html>";
    }

    private String buildPaymentBillEmail(String customerName, String specialistName, String transactionNo, String transactionTime, BigDecimal totalAmount) {
        return "<!DOCTYPE html>" +
                "<html><head><style>" +
                "body { font-family: Arial, sans-serif; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; }" +
                ".header { background-color: #ff7e9d; color: white; padding: 10px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { padding: 20px; background-color: white; border-radius: 0 0 5px 5px; }" +
                ".footer { text-align: center; font-size: 12px; color: #777; margin-top: 20px; }" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'><h2>Hóa Đơn Thanh Toán</h2></div>" +
                "<div class='content'>" +
                "<p>Xin chào " + customerName + ",</p>" +
                "<p>Cảm ơn bạn đã thanh toán dịch vụ tại Beautya với chuyên viên <strong>" + specialistName + "</strong>. Dưới đây là thông tin hóa đơn của bạn:</p>" +
                "<p><strong>Mã tra cứu:</strong> " + transactionNo + "</p>" +
                "<p><strong>Thời gian giao dịch:</strong> " + transactionTime + "</p>" +
                "<p><strong>Tổng số tiền:</strong> " + totalAmount + " VNĐ</p>" +
                "<p>Nếu bạn có bất kỳ thắc mắc nào, vui lòng liên hệ với chúng tôi.</p>" +
                "</div>" +
                "<div class='footer'>© 2025 Beautya. All rights reserved.</div>" +
                "</div></body></html>";
    }
}