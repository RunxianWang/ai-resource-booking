package com.wrx.booking.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt-secret:local-pr1-secret-change-me}") String secret,
            @Value("${app.jwt-expiration-seconds:7200}") long expirationSeconds
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(AuthenticatedUser user) {
        String header = ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long expiresAt = Instant.now().getEpochSecond() + expirationSeconds;
        String roles = user.roles().stream().sorted().collect(Collectors.joining(","));
        String payload = String.format("{\"sub\":%d,\"username\":\"%s\",\"roles\":\"%s\",\"exp\":%d}", user.id(), user.username(), roles, expiresAt);
        String unsigned = header + "." + ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
    }

    public AuthenticatedUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !java.security.MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), DECODER.decode(parts[2]))) {
                throw new IllegalArgumentException("invalid token");
            }
            String payload = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(value(payload, "exp"));
            if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException("token expired");
            Long id = Long.valueOf(value(payload, "sub"));
            String username = value(payload, "username");
            Set<String> roles = Arrays.stream(value(payload, "roles").split(","))
                    .filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
            return new AuthenticatedUser(id, username, roles);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid token", e);
        }
    }

    private String value(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\":(?:\\\"([^\\\"]*)\\\"|([^,}]+))").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing claim: " + key);
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign JWT", e);
        }
    }
}
