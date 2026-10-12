
package com.orderflow.backend.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
@WebMvcTest(ProductController.class)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;

    @Test
    void getMissingProductReturns404() throws Exception {
        when(productRepository.findById(9999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(
                        "{\"error\":\"Product not found with id: 9999\"}"
                ));
    }
    
    @Test
    void createProductWithBlankNameReturns400() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/products")
                        .contentType(
                                org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "   ",
                                    "description": "Invalid product",
                                    "price": 100,
                                    "stockQuantity": 10
                                }
                                """)
        )
        .andExpect(status().isBadRequest())
        .andExpect(content().json("""
                {
                    "error": "Validation failed",
                    "details": {
                        "name": "Product name is required"
                    }
                }
                """));
    }
    
@Test
void createProductWithNegativePriceReturns400() throws Exception {
    mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/products")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "name": "Test Product",
                                "description": "Negative price",
                                "price": -100,
                                "stockQuantity": 10
                            }
                            """)
    )
    .andExpect(status().isBadRequest())
    .andExpect(content().json("""
            {
                "error": "Validation failed",
                "details": {
                    "price": "Product price cannot be negative"
                }
            }
            """));
}

@Test
void createProductWithNegativeStockReturns400() throws Exception {
    mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/products")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "name": "Test Product",
                                "description": "Negative stock",
                                "price": 100,
                                "stockQuantity": -5
                            }
                            """)
    )
    .andExpect(status().isBadRequest())
    .andExpect(content().json("""
            {
                "error": "Validation failed",
                "details": {
                    "stockQuantity": "Stock quantity cannot be negative"
                }
            }
            """));
}


@Test
void createProductWithValidDataReturnsCreatedProduct() throws Exception {
    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/products")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "name": "Mechanical Keyboard",
                                "description": "Test keyboard",
                                "price": 2499.00,
                                "stockQuantity": 10
                            }
                            """)
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
    .andExpect(jsonPath("$.description").value("Test keyboard"))
    .andExpect(jsonPath("$.price").value(2499.00))
    .andExpect(jsonPath("$.stockQuantity").value(10));
}


@Test
void getExistingProductReturnsProduct() throws Exception {
    Product product = new Product();
    product.setName("Mechanical Keyboard");
    product.setDescription("Test keyboard");
    product.setPrice(new java.math.BigDecimal("2499.00"));
    product.setStockQuantity(10);

    when(productRepository.findById(1L))
            .thenReturn(Optional.of(product));

    mockMvc.perform(get("/api/products/1"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                    {
                        "name": "Mechanical Keyboard",
                        "description": "Test keyboard",
                        "price": 2499.00,
                        "stockQuantity": 10
                    }
                    """));
}


@Test
void updateExistingProductReturnsUpdatedProduct() throws Exception {
    Product existingProduct = new Product();
    existingProduct.setName("Old Keyboard");
    existingProduct.setDescription("Old description");
    existingProduct.setPrice(new java.math.BigDecimal("1999.00"));
    existingProduct.setStockQuantity(5);

    when(productRepository.findById(1L))
            .thenReturn(Optional.of(existingProduct));

    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/products/1")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "name": "Mechanical Keyboard",
                                "description": "Updated description",
                                "price": 2499.00,
                                "stockQuantity": 10
                            }
                            """)
    )
    .andExpect(status().isOk())
    .andExpect(content().json("""
            {
                "name": "Mechanical Keyboard",
                "description": "Updated description",
                "price": 2499.00,
                "stockQuantity": 10
            }
            """));
}

@Test
void deleteExistingProductReturns204() throws Exception {
    Product product = new Product();
    product.setName("Mechanical Keyboard");
    product.setDescription("Test keyboard");
    product.setPrice(new java.math.BigDecimal("2499.00"));
    product.setStockQuantity(10);

    when(productRepository.findById(1L))
            .thenReturn(Optional.of(product));

    mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/products/1")
    )
    .andExpect(status().isNoContent());

    verify(productRepository).delete(product);
}



}
