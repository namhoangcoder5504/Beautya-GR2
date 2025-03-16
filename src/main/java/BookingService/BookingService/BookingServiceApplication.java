package BookingService.BookingService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class BookingServiceApplication {
    public static void main(String[] args) {
        // Đặt múi giờ mặc định cho JVM (dự phòng)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
//        System.out.println("Default TimeZone set to: " + TimeZone.getDefault().getID());

        SpringApplication.run(BookingServiceApplication.class, args);
    }
}