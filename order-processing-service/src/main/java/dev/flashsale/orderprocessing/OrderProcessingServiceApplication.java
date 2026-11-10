package dev.flashsale.orderprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderProcessingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingServiceApplication.class, args);
    }
}
