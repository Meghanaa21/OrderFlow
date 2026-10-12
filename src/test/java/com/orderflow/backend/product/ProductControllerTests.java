
package com.orderflow.backend.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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


}
