package com.itmo.infobez.web;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
