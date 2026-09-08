package cn.naiblog.carsearch;

import cn.naiblog.carsearch.config.CarProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(CarProperties.class)
@EnableScheduling
public class CarSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarSearchApplication.class, args);
    }
}
