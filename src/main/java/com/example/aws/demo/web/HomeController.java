package com.example.aws.demo.web;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing endpoint describing the demo API so the root URL returns something useful.
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "application", "aws.demo",
                "description", "Spring Boot demo exercising AWS RDS (MySQL), S3 and CloudWatch",
                "endpoints", List.of(
                        "GET    /api/products",
                        "GET    /api/products/{id}",
                        "POST   /api/products            (JSON: name, description, price)",
                        "PUT    /api/products/{id}",
                        "DELETE /api/products/{id}",
                        "POST   /api/products/{id}/image (multipart form field 'file') -> S3",
                        "GET    /api/products/{id}/image -> S3",
                        "GET    /actuator/health",
                        "GET    /actuator/metrics"));
    }
}
