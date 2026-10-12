
package com.orderflow.backend.order;

import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.OrderResponse;
import com.orderflow.backend.product.Product;
import com.orderflow.backend.product.ProductNotFoundException;
import com.orderflow.backend.product.ProductRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderCalculatesTotalAndReducesStock() {
        Product product = new Product();
        product.setName("Keyboard");
        product.setDescription("Mechanical keyboard");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStockQuantity(10);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", 1L);
                    return order;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest.OrderItemRequest item =
                new CreateOrderRequest.OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        OrderResponse result = orderService.createOrder(request);

        assertEquals(1L, result.getId());
        assertEquals(new BigDecimal("2000.00"), result.getTotalAmount());
        assertEquals("PENDING", result.getStatus());
        assertEquals(8, product.getStockQuantity());

        assertEquals(1, result.getItems().size());
        assertEquals("Keyboard", result.getItems().get(0).getProductName());
        assertEquals(2, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("1000.00"),
                result.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("2000.00"),
                result.getItems().get(0).getSubtotal());

        verify(orderRepository).save(any(Order.class));
        verify(productRepository).save(product);
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    void createOrderThrowsExceptionWhenProductDoesNotExist() {
        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        CreateOrderRequest.OrderItemRequest item =
                new CreateOrderRequest.OrderItemRequest();
        item.setProductId(999L);
        item.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        assertThrows(
                ProductNotFoundException.class,
                () -> orderService.createOrder(request)
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createOrderRejectsInsufficientStock() {
        Product product = new Product();
        product.setName("Keyboard");
        product.setDescription("Mechanical keyboard");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStockQuantity(1);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        CreateOrderRequest.OrderItemRequest item =
                new CreateOrderRequest.OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getOrderByIdReturnsOrderResponse() {
        Order order = new Order();
        order.setStatus("PENDING");
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setCreatedAt(LocalDateTime.now());

        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findAllByOrderId(1L))
                .thenReturn(List.of());

        OrderResponse result = orderService.getOrderById(1L);

        assertEquals("PENDING", result.getStatus());
        assertEquals(
                0,
                new BigDecimal("20.00").compareTo(result.getTotalAmount())
        );

        verify(orderItemRepository).findAllByOrderId(1L);
    }

    @Test
    void getOrderByIdThrowsExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrderById(99L)
        );
    }
}
