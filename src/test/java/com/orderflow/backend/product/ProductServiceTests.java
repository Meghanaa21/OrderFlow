
package com.orderflow.backend.product;
import com.orderflow.backend.product.dto.UpdateProductRequest;
import com.orderflow.backend.product.dto.CreateProductRequest;
import com.orderflow.backend.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void getAllProductsReturnsProductResponses() {
        Product product = new Product();
        product.setName("Mechanical Keyboard");
        product.setDescription("Test keyboard");
        product.setPrice(new BigDecimal("2499.00"));
        product.setStockQuantity(10);

        when(productRepository.findAll())
                .thenReturn(List.of(product));

        List<ProductResponse> result = productService.getAllProducts();

        assertEquals(1, result.size());
        assertEquals("Mechanical Keyboard", result.get(0).getName());
        assertEquals("Test keyboard", result.get(0).getDescription());
        assertEquals(new BigDecimal("2499.00"), result.get(0).getPrice());
        assertEquals(10, result.get(0).getStockQuantity());

        verify(productRepository).findAll();
    }
    @Test
    void createProductReturnsSavedProductResponse() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Mechanical Keyboard");
        request.setDescription("Test keyboard");
        request.setPrice(new BigDecimal("2499.00"));
        request.setStockQuantity(10);

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product saved = invocation.getArgument(0);
                    return saved;
                });

        ProductResponse result = productService.createProduct(request);

        assertEquals("Mechanical Keyboard", result.getName());
        assertEquals("Test keyboard", result.getDescription());
        assertEquals(new BigDecimal("2499.00"), result.getPrice());
        assertEquals(10, result.getStockQuantity());

        verify(productRepository).save(any(Product.class));
    }
    @Test
    void getProductByIdReturnsProductResponse() {
        Product product = new Product();
        product.setName("Mechanical Keyboard");
        product.setDescription("Test keyboard");
        product.setPrice(new BigDecimal("2499.00"));
        product.setStockQuantity(10);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        ProductResponse result = productService.getProductById(1L);

        assertEquals("Mechanical Keyboard", result.getName());
        assertEquals("Test keyboard", result.getDescription());
        assertEquals(new BigDecimal("2499.00"), result.getPrice());
        assertEquals(10, result.getStockQuantity());

        verify(productRepository).findById(1L);
    }
    @Test
    void getProductByIdThrowsExceptionWhenProductDoesNotExist() {
        when(productRepository.findById(9999L))
                .thenReturn(java.util.Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductById(9999L)
        );

        verify(productRepository).findById(9999L);
    }
    
@Test
    void updateProductUpdatesExistingProduct() {
        Product existingProduct = new Product();
        existingProduct.setName("Old Keyboard");
        existingProduct.setDescription("Old description");
        existingProduct.setPrice(new BigDecimal("1999.00"));
        existingProduct.setStockQuantity(5);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(existingProduct));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("New Keyboard");
        request.setDescription("Updated description");
        request.setPrice(new BigDecimal("2499.00"));
        request.setStockQuantity(10);

        ProductResponse result = productService.updateProduct(1L, request);

        assertEquals("New Keyboard", result.getName());
        assertEquals("Updated description", result.getDescription());
        assertEquals(new BigDecimal("2499.00"), result.getPrice());
        assertEquals(10, result.getStockQuantity());

        verify(productRepository).findById(1L);
        verify(productRepository).save(existingProduct);
    }
    
@Test
    void deleteProductDeletesExistingProduct() {
        Product product = new Product();
        product.setName("Mechanical Keyboard");
        product.setDescription("Test keyboard");
        product.setPrice(new BigDecimal("2499.00"));
        product.setStockQuantity(10);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        verify(productRepository).findById(1L);
        verify(productRepository).delete(product);
    }
    @Test
    void deleteProductThrowsExceptionWhenProductDoesNotExist() {
        when(productRepository.findById(9999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.deleteProduct(9999L)
        );

        verify(productRepository).findById(9999L);
        verify(productRepository, never()).delete(any(Product.class));
    }

}
