package com.orderflow.backend.user;

import com.orderflow.backend.user.dto.RegisterRequest;
import com.orderflow.backend.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerHashesPasswordAndNormalizesEmail() {
        RegisterRequest request = new RegisterRequest(
            " Test Customer ",
            " CUSTOMER@EXAMPLE.COM ",
            "securePassword123"
        );

        when(userRepository.existsByEmail("customer@example.com"))
            .thenReturn(false);

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return user;
            });

        UserResponse response = userService.register(request);

        verify(userRepository).save(argThat(user ->
            user.getName().equals("Test Customer")
                && user.getEmail().equals("customer@example.com")
                && passwordEncoder.matches(
                    "securePassword123",
                    user.getPasswordHash()
                )
                && !user.getPasswordHash().equals("securePassword123")
        ));

        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.name()).isEqualTo("Test Customer");
        assertThat(response.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void registerRejectsExistingEmail() {
        RegisterRequest request = new RegisterRequest(
            "Test Customer",
            "customer@example.com",
            "securePassword123"
        );

        when(userRepository.existsByEmail("customer@example.com"))
            .thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerDoesNotExposePasswordHashInResponse() {
        RegisterRequest request = new RegisterRequest(
            "Test Customer",
            "customer@example.com",
            "securePassword123"
        );

        when(userRepository.existsByEmail("customer@example.com"))
            .thenReturn(false);

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.register(request);

        assertThat(response.toString())
            .doesNotContain("securePassword123");
    }
}