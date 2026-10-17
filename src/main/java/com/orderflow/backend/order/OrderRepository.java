
package com.orderflow.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUser_EmailIgnoreCase(
            Long id,
            String email
    );
}
