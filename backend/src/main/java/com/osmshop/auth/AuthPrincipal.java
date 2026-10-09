package com.osmshop.auth;

public record AuthPrincipal(long id, String email, String fullName, String role) {
}
