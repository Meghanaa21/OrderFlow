
package com.orderflow.backend.user.dto;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}
