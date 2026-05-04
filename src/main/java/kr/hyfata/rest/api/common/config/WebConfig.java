package kr.hyfata.rest.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            String[] origins = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);

            registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);

            registry.addMapping("/oauth/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
