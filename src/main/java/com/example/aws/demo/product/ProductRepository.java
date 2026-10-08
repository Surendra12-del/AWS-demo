package com.example.aws.demo.product;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Product}. Backed by RDS (MySQL).
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
