package com.itmo.infobezitmo.web;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
