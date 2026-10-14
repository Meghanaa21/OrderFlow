
package com.orderflow.backend;
import com.orderflow.backend.order.Order;
import com.orderflow.backend.order.OrderItem;
import com.orderflow.backend.order.OrderRepository;
import com.orderflow.backend.order.OrderItemRepository;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.orderflow.backend.product.Product;
import com.orderflow.backend.product.ProductRepository;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class OrderFlowDatabaseIntegrationTests {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;
    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("orderflow_test")
                    .withUsername("orderflow_test")
                    .withPassword("test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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
    void productCanBeSavedAndRetrievedFromPostgres() {
        Product product = new Product();
        product.setName("Integration Test Product");
        product.setDescription("Created in a PostgreSQL container");
        product.setPrice(new java.math.BigDecimal("49.99"));
        product.setStockQuantity(10);

        Product savedProduct = productRepository.saveAndFlush(product);

        assertThat(savedProduct.getId()).isNotNull();

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
        Product product = new Product();
        product.setName("Order Integration Product");
        product.setDescription("Product for order persistence test");
        product.setPrice(new java.math.BigDecimal("25.00"));
        product.setStockQuantity(5);

        Product savedProduct = productRepository.saveAndFlush(product);

        Order order = new Order();
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(new java.math.BigDecimal("50.00"));
        order.setCreatedAt(java.time.LocalDateTime.now());

        Order savedOrder = orderRepository.saveAndFlush(order);

        OrderItem item = new OrderItem();
        item.setOrder(savedOrder);
        item.setProduct(savedProduct);
        item.setQuantity(2);
        item.setUnitPrice(new java.math.BigDecimal("25.00"));

        OrderItem savedItem = orderItemRepository.saveAndFlush(item);

        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedItem.getId()).isNotNull();
        assertThat(savedItem.getOrder().getId()).isEqualTo(savedOrder.getId());
        assertThat(savedItem.getProduct().getId()).isEqualTo(savedProduct.getId());
        assertThat(savedItem.getQuantity()).isEqualTo(2);
    }
}
