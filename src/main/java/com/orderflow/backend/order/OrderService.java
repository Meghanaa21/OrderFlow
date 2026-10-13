
package com.orderflow.backend.order;

import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.CreateOrderRequest.OrderItemRequest;
import com.orderflow.backend.order.dto.OrderResponse;
import com.orderflow.backend.order.dto.OrderResponse.OrderItemResponse;
import com.orderflow.backend.product.Product;
import com.orderflow.backend.product.ProductNotFoundException;
import com.orderflow.backend.product.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
                    new ProductNotFoundException(
                            itemRequest.getProductId()
                    )
            );

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: "
                                + product.getName()
                );
            }

            BigDecimal itemTotal = product.getPrice()
                    .multiply(
                            BigDecimal.valueOf(itemRequest.getQuantity())
                    );

            totalAmount = totalAmount.add(itemTotal);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(product.getPrice());

            orderItems.add(item);
        }

        // Create the order.
        Order order = new Order();
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(totalAmount);
        order.setCreatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // Reduce stock and associate each item with the order.
        for (OrderItem item : orderItems) {

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

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        List<OrderItem> items =
                orderItemRepository.findAllByOrderId(id);

        return toResponse(order, items);
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        // Only pending orders can be cancelled.
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new OrderCannotBeCancelledException(
                    id,
                    order.getStatus()
            );
        }

        List<OrderItem> items =
                orderItemRepository.findAllByOrderId(id);

        // Restore stock for every item in the order.
        for (OrderItem item : items) {
            Product product = item.getProduct();

            product.setStockQuantity(
                    product.getStockQuantity() + item.getQuantity()
            );

            productRepository.save(product);
        }

        // Mark the order as cancelled.
        order.setStatus(Order.STATUS_CANCELLED);
        Order savedOrder = orderRepository.save(order);

        return toResponse(savedOrder, items);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, String newStatus) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        String currentStatus = order.getStatus();

        // Allow only the next valid status transition.
        boolean validTransition =
                (Order.STATUS_PENDING.equals(currentStatus)
                        && Order.STATUS_CONFIRMED.equals(newStatus))
                || (Order.STATUS_CONFIRMED.equals(currentStatus)
                        && Order.STATUS_SHIPPED.equals(newStatus))
                || (Order.STATUS_SHIPPED.equals(currentStatus)
                        && Order.STATUS_DELIVERED.equals(newStatus));

        if (!validTransition) {
            throw new InvalidOrderStatusTransitionException(
                    id,
                    currentStatus,
                    newStatus
            );
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> items =
                orderItemRepository.findAllByOrderId(id);

        return toResponse(savedOrder, items);
    }

    private OrderResponse toResponse(
            Order order,
            List<OrderItem> items) {

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
