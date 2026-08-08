package com.funix.swp490x.mrs.web.api.admin;

/** Outcome of create / resend when credentials mail is attempted. */
public record CredentialsDeliveryResponse(
        UserResponse user,
        String message,
        boolean emailDelivered) {
}
