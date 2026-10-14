package com.orderflow.backend.user;

import static org.mockito.Mockito.when;
import com.orderflow.backend.user.EmailAlreadyRegisteredException;
import com.orderflow.backend.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;


    @MockitoBean
    private UserService userService;

    @Test
    void registerReturnsCreatedWhenRequestIsValid() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "Test Customer",
            "customer@example.com",
            "securePassword123"
        );

        when(userService.register(any(RegisterRequest.class)))
            .thenReturn(new com.orderflow.backend.user.dto.UserResponse(
                1L,
                "Test Customer",
                "customer@example.com",
                "CUSTOMER",
                LocalDateTime.now()
            ));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Test Customer",
                        "email": "customer@example.com",
                        "password": "securePassword123"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.email").value("customer@example.com"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void registerReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        String invalidRequest = """
            {
                "name": "Test Customer",
                "email": "not-an-email",
                "password": "securePassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void registerReturnsBadRequestWhenPasswordIsTooShort() throws Exception {
        String invalidRequest = """
            {
                "name": "Test Customer",
                "email": "customer@example.com",
                "password": "short"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
    @Test
    void registerReturnsConflictWhenEmailAlreadyExists() throws Exception {
        String request = """
            {
                "name": "Test Customer",
                "email": "customer@example.com",
                "password": "securePassword123"
            }
            """;

        when(userService.register(any(RegisterRequest.class)))
            .thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());

        verify(userService).register(any(RegisterRequest.class));
    }
}