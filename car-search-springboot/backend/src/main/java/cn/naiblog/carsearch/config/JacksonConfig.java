package cn.naiblog.carsearch.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeFormatCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("LocalDateTimeFormat");
            module.addSerializer(java.time.LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
            builder.addModule(module);
        };
    }
}