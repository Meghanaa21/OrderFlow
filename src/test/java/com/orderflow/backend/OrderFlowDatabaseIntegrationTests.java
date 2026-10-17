
package com.orderflow.backend;

import com.orderflow.backend.order.Order;
import com.orderflow.backend.order.OrderItem;
import com.orderflow.backend.order.OrderItemRepository;
import com.orderflow.backend.order.OrderRepository;
import com.orderflow.backend.order.OrderService;
import com.orderflow.backend.order.dto.CreateOrderRequest;
import com.orderflow.backend.order.dto.CreateOrderRequest.OrderItemRequest;
import com.orderflow.backend.product.Product;
import com.orderflow.backend.product.ProductRepository;
import com.orderflow.backend.user.User;
import com.orderflow.backend.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.Page;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class OrderFlowDatabaseIntegrationTests {

    private static final String CUSTOMER_EMAIL = "customer@example.com";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("orderflow_test")
                    .withUsername("orderflow_test")
                    .withPassword("test_password");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE order_items, orders, products, users "
                        + "RESTART IDENTITY CASCADE"
        );

        userRepository.saveAndFlush(
                new User(
                        "Test Customer",
                        CUSTOMER_EMAIL,
                        "test-password-hash"
                )
        );
    }

    @Test
    void postgresContainerStartsSuccessfully() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void flywayCreatesExpectedTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('products', 'orders', 'order_items')
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    void failedOrderItemInsertRollsBackOrderAndStockChanges() {
        Product product = productRepository.saveAndFlush(
                createProduct("Rollback Product", "10.00", 10)
        );

        // Force an order-item insert failure inside createOrder().
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_test_order_item()
                RETURNS TRIGGER AS $$
                BEGIN
                    RAISE EXCEPTION 'intentional integration test failure';
                END;
                $$ LANGUAGE plpgsql
                """);

        jdbcTemplate.execute("""
                CREATE TRIGGER reject_test_order_item_trigger
                BEFORE INSERT ON order_items
                FOR EACH ROW
                EXECUTE FUNCTION reject_test_order_item()
                """);

        try {
            assertThatThrownBy(() ->
                    orderService.createOrder(
                            createOrderRequest(product.getId(), 2),
                            CUSTOMER_EMAIL
                    )
            ).isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS "
                            + "reject_test_order_item_trigger ON order_items"
            );

            jdbcTemplate.execute(
                    "DROP FUNCTION IF EXISTS reject_test_order_item()"
            );
        }

        // Verify that the order and stock updates were rolled back.
        Product reloadedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(reloadedProduct.getStockQuantity()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
    }

    @Test
    void productCanBeSavedAndRetrievedFromPostgres() {
        Product product = createProduct(
                "Integration Test Product",
                "49.99",
                10
        );

        Product savedProduct = productRepository.saveAndFlush(product);

        Product retrievedProduct = productRepository
                .findById(savedProduct.getId())
                .orElseThrow();

        assertThat(retrievedProduct.getName())
                .isEqualTo("Integration Test Product");
        assertThat(retrievedProduct.getPrice())
                .isEqualByComparingTo("49.99");
        assertThat(retrievedProduct.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void orderAndOrderItemCanBePersistedWithRelationships() {
        Product product = productRepository.saveAndFlush(
                createProduct("Order Integration Product", "25.00", 5)
        );

        Order order = new Order();
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setCreatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.saveAndFlush(order);

        OrderItem item = new OrderItem();
        item.setOrder(savedOrder);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("25.00"));

        OrderItem savedItem = orderItemRepository.saveAndFlush(item);

        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedItem.getId()).isNotNull();
        assertThat(savedItem.getOrder().getId())
                .isEqualTo(savedOrder.getId());
        assertThat(savedItem.getProduct().getId())
                .isEqualTo(product.getId());
        assertThat(savedItem.getQuantity()).isEqualTo(2);
    }

    @Test
    void createOrderReducesStockAndPersistsOrderItems() {
        Product product = productRepository.saveAndFlush(
                createProduct("Order Service Product", "10.00", 10)
        );

        CreateOrderRequest request = createOrderRequest(
                product.getId(),
                2
        );

        var response = orderService.createOrder(request, CUSTOMER_EMAIL);

        Product updatedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Order.STATUS_PENDING);
        assertThat(response.getTotalAmount())
                .isEqualByComparingTo("20.00");
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(8);
        assertThat(orderItemRepository.findAllByOrderId(response.getId()))
                .hasSize(1);

        // Verify ownership directly in PostgreSQL.
        // This avoids accessing a lazy-loaded User proxy after its session closes.
        String persistedCustomerEmail = jdbcTemplate.queryForObject(
                "SELECT u.email FROM orders o "
                        + "JOIN users u ON u.id = o.user_id "
                        + "WHERE o.id = ?",
                String.class,
                response.getId()
        );

        assertThat(persistedCustomerEmail).isEqualTo(CUSTOMER_EMAIL);
    }

    @Test
    void createOrderWithInsufficientStockDoesNotCreateAnOrderOrChangeStock() {
        Product product = productRepository.saveAndFlush(
                createProduct("Low Stock Product", "10.00", 1)
        );

        CreateOrderRequest request = createOrderRequest(
                product.getId(),
                2
        );

        assertThatThrownBy(() ->
                orderService.createOrder(request, CUSTOMER_EMAIL)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");

        Product unchangedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(unchangedProduct.getStockQuantity()).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
    }

    @Test
    void cancellingOrderRestoresStock() {
        Product product = productRepository.saveAndFlush(
                createProduct("Cancellation Product", "10.00", 10)
        );

        var createdOrder = orderService.createOrder(
                createOrderRequest(product.getId(), 3),
                CUSTOMER_EMAIL
        );

        assertThat(
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStockQuantity()
        ).isEqualTo(7);

        orderService.cancelOrder(
                createdOrder.getId(),
                CUSTOMER_EMAIL,
                false
        );

        Product restoredProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(restoredProduct.getStockQuantity()).isEqualTo(10);

        assertThat(
                orderService.getOrderById(
                        createdOrder.getId(),
                        CUSTOMER_EMAIL,
                        false
                ).getStatus()
        ).isEqualTo(Order.STATUS_CANCELLED);
    }
    
        @Test
        void customerSeesOnlyTheirOwnOrdersInDatabasePagination() {
        User anotherCustomer = userRepository.saveAndFlush(
                new User(
                        "Another Customer",
                        "another@example.com",
                        "test-password-hash"
                )
        );

        Order firstOrder = createTestOrder(
                userRepository.findByEmail(CUSTOMER_EMAIL).orElseThrow(),
                "10.00",
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        Order secondOrder = createTestOrder(
                anotherCustomer,
                "20.00",
                LocalDateTime.of(2026, 1, 2, 10, 0)
        );

        Page<com.orderflow.backend.order.dto.OrderResponse> result =
                orderService.getOrders(CUSTOMER_EMAIL, false, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(com.orderflow.backend.order.dto.OrderResponse::getId)
                .containsExactly(firstOrder.getId())
                .doesNotContain(secondOrder.getId());
        }

        @Test
        void adminCanSeeOrdersFromMultipleCustomers() {
        User firstCustomer = userRepository.findByEmail(CUSTOMER_EMAIL)
                .orElseThrow();

        User secondCustomer = userRepository.saveAndFlush(
                new User(
                        "Another Customer",
                        "another@example.com",
                        "test-password-hash"
                )
        );

        createTestOrder(
                firstCustomer,
                "10.00",
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        createTestOrder(
                secondCustomer,
                "20.00",
                LocalDateTime.of(2026, 1, 2, 10, 0)
        );

        Page<com.orderflow.backend.order.dto.OrderResponse> result =
                orderService.getOrders("admin@example.com", true, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void orderPaginationReturnsCorrectPageAndTotalCount() {
        User customer = userRepository.findByEmail(CUSTOMER_EMAIL)
                .orElseThrow();

        createTestOrder(
                customer,
                "10.00",
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        createTestOrder(
                customer,
                "20.00",
                LocalDateTime.of(2026, 1, 2, 10, 0)
        );

        createTestOrder(
                customer,
                "30.00",
                LocalDateTime.of(2026, 1, 3, 10, 0)
        );

        Page<com.orderflow.backend.order.dto.OrderResponse> result =
                orderService.getOrders(CUSTOMER_EMAIL, false, 0, 2);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
        }

        private Order createTestOrder(
                User user,
                String amount,
                LocalDateTime createdAt) {

        Order order = new Order();
        order.setUser(user);
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(new BigDecimal(amount));
        order.setCreatedAt(createdAt);

        return orderRepository.saveAndFlush(order);
        }

    private Product createProduct(
            String name,
            String price,
            int stockQuantity
    ) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Created for integration testing");
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stockQuantity);

        return product;
    }

    private CreateOrderRequest createOrderRequest(
            Long productId,
            int quantity
    ) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        return request;
    }

    @Test
    void userCanBeSavedAndRetrievedFromPostgres() {
        User user = new User(
                "Another Customer",
                "another@example.com",
                "test-password-hash"
        );

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(userRepository.findByEmail("another@example.com"))
                .isPresent()
                .get()
                .extracting(User::getName)
                .isEqualTo("Another Customer");
    }

    @Test
    void duplicateUserEmailIsRejectedByPostgres() {
        userRepository.saveAndFlush(
                new User(
                        "Customer One",
                        "duplicate@example.com",
                        "hash-one"
                )
        );

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(
                        new User(
                                "Customer Two",
                                "duplicate@example.com",
                                "hash-two"
                        )
                )
        ).isInstanceOf(RuntimeException.class);
    }
}
