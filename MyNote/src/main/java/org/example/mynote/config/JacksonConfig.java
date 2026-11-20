package org.example.mynote.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * JSON 序列化配置。
 *
 * <p>application.yml 里的 spring.jackson.date-format 只作用于 java.util.Date，
 * 对 LocalDateTime 无效——不加这层定制，前端会收到 ISO 的 "2026-05-11T09:12:00"，
 * 与接口契约里 "2026-05-11 09:12:00" 的示例不一致。在此统一，避免每个 VO 单独打注解。</p>
 *
 * @author MyNote
 */
@Configuration
public class JacksonConfig {

    /**
     * 全局时间格式
     */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式（热力图、趋势按天聚合时使用）
     */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 注册 LocalDateTime 的序列化与反序列化器。
     *
     * @return 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return builder -> {
            builder.serializerByType(java.time.LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            builder.deserializerByType(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
            builder.serializerByType(java.time.LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(
                            DateTimeFormatter.ofPattern(DATE_PATTERN)));
            builder.deserializerByType(java.time.LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(
                            DateTimeFormatter.ofPattern(DATE_PATTERN)));
        };
    }
}
