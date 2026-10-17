package com.orderflow.backend.order;

import com.orderflow.backend.config.JwtService;
import com.orderflow.backend.config.RestAuthenticationEntryPoint;
import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.junit.jupiter.api.AfterEach;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(OrderControllerTests.MvcTestConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTests {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrderReturnsCreatedResponse() throws Exception {
        OrderResponse response = new OrderResponse(
                1L, "PENDING", new BigDecimal("20.00"),
                LocalDateTime.now(), List.of()
        );

        when(orderService.createOrder(any(CreateOrderRequest.class), eq(CUSTOMER_EMAIL)))
                .thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .with(testPrincipal(CUSTOMER_EMAIL, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(20.00));

        verify(orderService).createOrder(any(CreateOrderRequest.class), eq(CUSTOMER_EMAIL));
    }

    @Test
    void createOrderRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(testPrincipal(CUSTOMER_EMAIL, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        // Using empty items array to trigger @Valid failure
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // Helper method to inject the mock user into the request
    private RequestPostProcessor testPrincipal(String username, String role) {
        return request -> {
            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                    username, "password", List.of(new SimpleGrantedAuthority(role)));
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities()));
            return request;
        };
    }

    // Configures Spring MVC to resolve @AuthenticationPrincipal since Security filters are disabled
    @TestConfiguration
    static class MvcTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    if (webRequest.getUserPrincipal() instanceof UsernamePasswordAuthenticationToken token) {
                        return token.getPrincipal();
                    }
                    return null;
                }
            });
        }
    }
}