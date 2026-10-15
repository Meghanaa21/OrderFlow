
package com.orderflow.backend.product;
import com.orderflow.backend.config.JwtService;
import com.orderflow.backend.product.dto.CreateProductRequest;
import com.orderflow.backend.product.dto.ProductResponse;
import com.orderflow.backend.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void getMissingProductReturns404() throws Exception {
        when(productService.getProductById(9999L))
                .thenThrow(new ProductNotFoundException(9999L));

        mockMvc.perform(get("/api/products/9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(
                        "{\"error\":\"Product not found with id: 9999\"}"
                ));
    }

    @Test
    void createProductWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "   ",
                                    "description": "Invalid product",
                                    "price": 100,
                                    "stockQuantity": 10
                                }
                                """))
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
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test Product",
                                    "description": "Negative price",
                                    "price": -100,
                                    "stockQuantity": 10
                                }
                                """))
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
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test Product",
                                    "description": "Negative stock",
                                    "price": 100,
                                    "stockQuantity": -5
                                }
                                """))
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
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenReturn(new ProductResponse(
                        1L, "Mechanical Keyboard", "Test keyboard",
                        new BigDecimal("2499.00"), 10
                ));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Mechanical Keyboard",
                                    "description": "Test keyboard",
                                    "price": 2499.00,
                                    "stockQuantity": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.description").value("Test keyboard"))
                .andExpect(jsonPath("$.price").value(2499.00))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void getExistingProductReturnsProduct() throws Exception {
        when(productService.getProductById(1L))
                .thenReturn(new ProductResponse(
                        1L, "Mechanical Keyboard", "Test keyboard",
                        new BigDecimal("2499.00"), 10
                ));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            "id": 1,
                            "name": "Mechanical Keyboard",
                            "description": "Test keyboard",
                            "price": 2499.00,
                            "stockQuantity": 10
                        }
                        """));
    }

    @Test
    void updateExistingProductReturnsUpdatedProduct() throws Exception {
        when(productService.updateProduct(
                eq(1L), any(UpdateProductRequest.class)))
                .thenReturn(new ProductResponse(
                        1L, "Mechanical Keyboard", "Updated description",
                        new BigDecimal("2499.00"), 10
                ));

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Mechanical Keyboard",
                                    "description": "Updated description",
                                    "price": 2499.00,
                                    "stockQuantity": 10
                                }
                                """))
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
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }
}
