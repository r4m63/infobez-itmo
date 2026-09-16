package com.itmo.infobezitmo.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Выпуск и проверка JWT (HS256, симметричный секрет из переменной окружения JWT_SECRET).
 * Токен: header.payload.signature. В payload: iss, sub (логин), iat, exp, roles.
 */
@Service
public final class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl}") Duration ttl) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters (256 bits for HS256)");
        }
        this.issuer = issuer;
        this.ttl = ttl;
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Проверяются подпись, срок действия (exp) и издатель (iss).
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.decoder = nimbus;
    }

    public String issue(Authentication authentication) {
        Instant now = Instant.now();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** @throws JwtException если подпись неверна, токен просрочен или издатель не совпадает. */
    public Jwt verify(String token) throws JwtException {
        return decoder.decode(token);
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
