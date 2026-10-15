
package com.orderflow.backend.order;
import com.orderflow.backend.config.JwtService;

import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.OrderResponse;
import com.orderflow.backend.order.dto.UpdateOrderStatusRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @Test
    void createOrderReturnsCreatedResponse() throws Exception {
        OrderResponse response = new OrderResponse(
                1L,
                "PENDING",
                new BigDecimal("20.00"),
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.createOrder(any(CreateOrderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(20.00));
    }

    @Test
    void createOrderRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderByIdReturnsOrderResponse() throws Exception {
        OrderResponse.OrderItemResponse item =
                new OrderResponse.OrderItemResponse(
                        10L,
                        "Wireless Mouse",
                        2,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00")
                );

        OrderResponse response = new OrderResponse(
                1L,
                "PENDING",
                new BigDecimal("20.00"),
                LocalDateTime.now(),
                List.of(item)
        );

        when(orderService.getOrderById(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(20.00))
                .andExpect(jsonPath("$.items[0].productId").value(10))
                .andExpect(jsonPath("$.items[0].productName")
                        .value("Wireless Mouse"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(20.00));

        verify(orderService).getOrderById(1L);
    }

    @Test
    void getOrderByIdReturnsNotFoundWhenOrderDoesNotExist()
            throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Order not found with id: 99"));
    }

    @Test
    void cancelOrderReturnsCancelledResponse() throws Exception {
        OrderResponse response = new OrderResponse(
                1L,
                "CANCELLED",
                new BigDecimal("20.00"),
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.cancelOrder(1L))
                .thenReturn(response);

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.totalAmount").value(20.00));

        verify(orderService).cancelOrder(1L);
    }

    @Test
    void cancelOrderReturnsNotFoundWhenOrderDoesNotExist()
            throws Exception {
        when(orderService.cancelOrder(99L))
                .thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(delete("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Order not found with id: 99"));
    }

    @Test
    void cancelOrderReturnsBadRequestWhenOrderCannotBeCancelled()
            throws Exception {
        when(orderService.cancelOrder(1L))
                .thenThrow(new OrderCannotBeCancelledException(
                        1L, "CANCELLED"));

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Order 1 cannot be cancelled because its status is CANCELLED"));
    }

    @Test
    void updateOrderStatusReturnsUpdatedOrder() throws Exception {
        OrderResponse response = new OrderResponse(
                1L,
                "CONFIRMED",
                new BigDecimal("20.00"),
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.updateOrderStatus(
                1L, "CONFIRMED")).thenReturn(response);

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(orderService).updateOrderStatus(
                1L, "CONFIRMED");
    }

    @Test
    void updateOrderStatusReturnsBadRequestForInvalidTransition()
            throws Exception {
        when(orderService.updateOrderStatus(
                1L, "SHIPPED"))
                .thenThrow(new InvalidOrderStatusTransitionException(
                        1L, "PENDING", "SHIPPED"));

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Cannot change order 1 from PENDING to SHIPPED"));
    }

    @Test
    void updateOrderStatusReturnsNotFoundForMissingOrder()
            throws Exception {
        when(orderService.updateOrderStatus(
                99L, "CONFIRMED"))
                .thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(patch("/api/orders/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Order not found with id: 99"));
    }

    @Test
    void updateOrderStatusRejectsBlankStatus() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
