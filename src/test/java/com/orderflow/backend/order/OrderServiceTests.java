
package com.orderflow.backend.order;

import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.OrderResponse;
import com.orderflow.backend.product.Product;
import com.orderflow.backend.product.ProductNotFoundException;
import com.orderflow.backend.product.ProductRepository;
import com.orderflow.backend.user.User;
import com.orderflow.backend.user.UserRepository;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    private static final String CUSTOMER_EMAIL = "customer@example.com";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private User createTestUser() {
        return new User(
                "Test Customer",
                CUSTOMER_EMAIL,
                "test-password-hash"
        );
    }

    @Test
    void createOrderCalculatesTotalAndReducesStock() {
        User user = createTestUser();

        when(userRepository.findByEmail(CUSTOMER_EMAIL))
                .thenReturn(Optional.of(user));

        Product product = new Product();
        product.setName("Keyboard");
        product.setDescription("Mechanical keyboard");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStockQuantity(10);
        ReflectionTestUtils.setField(product, "id", 1L);

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

        OrderResponse result =
                orderService.createOrder(request, CUSTOMER_EMAIL);

        assertEquals(1L, result.getId());
        assertEquals(
                new BigDecimal("2000.00"),
                result.getTotalAmount()
        );
        assertEquals(Order.STATUS_PENDING, result.getStatus());
        assertEquals(8, product.getStockQuantity());
        assertEquals(1, result.getItems().size());
        assertEquals("Keyboard", result.getItems().get(0).getProductName());
        assertEquals(2, result.getItems().get(0).getQuantity());
        assertEquals(
                new BigDecimal("1000.00"),
                result.getItems().get(0).getUnitPrice()
        );
        assertEquals(
                new BigDecimal("2000.00"),
                result.getItems().get(0).getSubtotal()
        );

        verify(orderRepository).save(argThat(order ->
                order.getUser() == user
        ));
        verify(productRepository).save(product);
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    void createOrderThrowsExceptionWhenProductDoesNotExist() {
        when(userRepository.findByEmail(CUSTOMER_EMAIL))
                .thenReturn(Optional.of(createTestUser()));

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
                () -> orderService.createOrder(request, CUSTOMER_EMAIL)
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createOrderRejectsInsufficientStock() {
        when(userRepository.findByEmail(CUSTOMER_EMAIL))
                .thenReturn(Optional.of(createTestUser()));

        Product product = new Product();
        product.setName("Keyboard");
        product.setDescription("Mechanical keyboard");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStockQuantity(1);
        ReflectionTestUtils.setField(product, "id", 1L);

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
                () -> orderService.createOrder(request, CUSTOMER_EMAIL)
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getOrderByIdReturnsOrderResponse() {
        Order order = new Order();
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setCreatedAt(LocalDateTime.now());
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findByIdAndUser_EmailIgnoreCase(
                1L, CUSTOMER_EMAIL
        )).thenReturn(Optional.of(order));

        when(orderItemRepository.findAllByOrderId(1L))
                .thenReturn(List.of());

        OrderResponse result =
                orderService.getOrderById(1L, CUSTOMER_EMAIL, false);

        assertEquals(Order.STATUS_PENDING, result.getStatus());
        assertEquals(
                0,
                new BigDecimal("20.00").compareTo(result.getTotalAmount())
        );

        verify(orderRepository).findByIdAndUser_EmailIgnoreCase(
                1L, CUSTOMER_EMAIL
        );
        verify(orderItemRepository).findAllByOrderId(1L);
    }

    @Test
    void getOrderByIdThrowsExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findByIdAndUser_EmailIgnoreCase(
                99L, CUSTOMER_EMAIL
        )).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrderById(
                        99L, CUSTOMER_EMAIL, false
                )
        );

        verify(orderRepository).findByIdAndUser_EmailIgnoreCase(
                99L, CUSTOMER_EMAIL
        );
        verify(orderItemRepository, never()).findAllByOrderId(anyLong());
    }

    @Test
    void cancelOrderRestoresStockAndUpdatesStatus() {
        Order order = new Order();
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(new BigDecimal("2000.00"));
        order.setCreatedAt(LocalDateTime.now());
        ReflectionTestUtils.setField(order, "id", 1L);

        Product product = new Product();
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStockQuantity(8);
        ReflectionTestUtils.setField(product, "id", 10L);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("1000.00"));

        when(orderRepository.findByIdAndUser_EmailIgnoreCase(
                1L, CUSTOMER_EMAIL
        )).thenReturn(Optional.of(order));

        when(orderItemRepository.findAllByOrderId(1L))
                .thenReturn(List.of(item));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result =
                orderService.cancelOrder(1L, CUSTOMER_EMAIL, false);

        assertEquals(Order.STATUS_CANCELLED, result.getStatus());
        assertEquals(10, product.getStockQuantity());

        verify(orderRepository).findByIdAndUser_EmailIgnoreCase(
                1L, CUSTOMER_EMAIL
        );
        verify(productRepository).save(product);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrderRejectsAlreadyCancelledOrder() {
        Order order = new Order();
        order.setStatus(Order.STATUS_CANCELLED);
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findByIdAndUser_EmailIgnoreCase(
                1L, CUSTOMER_EMAIL
        )).thenReturn(Optional.of(order));

        assertThrows(
                OrderCannotBeCancelledException.class,
                () -> orderService.cancelOrder(
                        1L, CUSTOMER_EMAIL, false
                )
        );

        verify(orderItemRepository, never()).findAllByOrderId(anyLong());
        verify(productRepository, never()).save(any(Product.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void cancelOrderThrowsExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findByIdAndUser_EmailIgnoreCase(
                99L, CUSTOMER_EMAIL
        )).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.cancelOrder(
                        99L, CUSTOMER_EMAIL, false
                )
        );

        verify(orderRepository).findByIdAndUser_EmailIgnoreCase(
                99L, CUSTOMER_EMAIL
        );
        verify(orderItemRepository, never()).findAllByOrderId(anyLong());
        verify(productRepository, never()).save(any(Product.class));
    }
}
