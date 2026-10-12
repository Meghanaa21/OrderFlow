
package com.orderflow.backend.order;

import com.orderflow.backend.product.Product;
import com.orderflow.backend.product.ProductNotFoundException;
import com.orderflow.backend.product.ProductRepository;
import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.CreateOrderRequest.OrderItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.orderflow.backend.order.dto.OrderResponse;
import com.orderflow.backend.order.dto.OrderResponse.OrderItemResponse;
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Validate every item before changing stock.
        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(
                    itemRequest.getProductId()
            ).orElseThrow(() ->
                    new ProductNotFoundException(itemRequest.getProductId())
            );

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: " + product.getName()
                );
            }

            BigDecimal itemTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            totalAmount = totalAmount.add(itemTotal);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(product.getPrice());

            orderItems.add(item);
        }

        Order order = new Order();
        order.setStatus("PENDING");
        order.setTotalAmount(totalAmount);
        order.setCreatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        for (int i = 0; i < orderItems.size(); i++) {
            OrderItem item = orderItems.get(i);
            item.setOrder(savedOrder);

            Product product = item.getProduct();
            product.setStockQuantity(
                    product.getStockQuantity() - item.getQuantity()
            );

            productRepository.save(product);
        }

        orderItemRepository.saveAll(orderItems);

        return toResponse(savedOrder, orderItems);
    }
    
    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getUnitPrice().multiply(
                                BigDecimal.valueOf(item.getQuantity())
                        )
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                itemResponses
        );
    }

}
