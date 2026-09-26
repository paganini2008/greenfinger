/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues and checks sign-in tokens. Nothing is stored: a token carries who it is for, what they may
 * do and when it expires, signed, and any node checks it by recomputing the signature. A per-process
 * map of random tokens would make a token valid only on the node that issued it, so the first
 * balanced request after signing in answers "Sign in first".
 *
 * <p>
 * Every node must share {@code GF_TOKEN_SECRET}, or each rejects the others' tokens. Without one a
 * random secret is generated and warned about: single node works, but tokens die with the process.
 *
 * <p>
 * A signed token cannot be un-signed, so signing out is kept in a small local set until it would
 * have expired -- honoured on this node only. The answer is short tokens, not a shared blacklist.
 *
 * @Description: TokenStore
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class TokenStore {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Duration validity;
    private final SecretKeySpec key;

    /** Tokens signed out here, and when they would have expired anyway. */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    public TokenStore(Duration validity, String secret) {
        this.validity = validity;
        this.key = new SecretKeySpec(secretBytes(secret), ALGORITHM);
    }

    private static byte[] secretBytes(String secret) {
        if (StringUtils.isNotBlank(secret)) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        log.warn("No greenfinger.security.token-secret is set, so one was generated for this"
                + " process. Sign-ins will not survive a restart and will not be accepted by"
                + " another node. Set GF_TOKEN_SECRET in .env to share one.");
        return generated;
    }

    public String issue(UserDetails user) {
        String roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .reduce((a, b) -> a + "," + b).orElse("");
        String payload = String.join("\n", user.getUsername(),
                String.valueOf(System.currentTimeMillis() + validity.toMillis()), roles);
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + ENCODER.encodeToString(sign(encoded));
    }

    public Optional<UserDetails> resolve(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String encoded = token.substring(0, dot);
        byte[] presented;
        byte[] payload;
        try {
            presented = DECODER.decode(token.substring(dot + 1));
            payload = DECODER.decode(encoded);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // constant time: comparing signatures with equals leaks where they first differ
        if (!MessageDigest.isEqual(sign(encoded), presented)) {
            return Optional.empty();
        }
        String[] parts = new String(payload, StandardCharsets.UTF_8).split("\n", 3);
        if (parts.length < 2) {
            return Optional.empty();
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        // <= rather than <: a token whose validity has run out to the millisecond is spent
        if (expiresAt <= System.currentTimeMillis()) {
            return Optional.empty();
        }
        if (revoked.containsKey(token)) {
            return Optional.empty();
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (parts.length > 2 && StringUtils.isNotBlank(parts[2])) {
            for (String role : parts[2].split(",")) {
                authorities.add(new SimpleGrantedAuthority(role.trim()));
            }
        }
        // the password is never in the token and is not needed to answer a request
        return Optional.of(new User(parts[0], "", authorities));
    }

    public void revoke(String token) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        resolve(token).ifPresent(user -> revoked.put(token,
                System.currentTimeMillis() + validity.toMillis()));
    }

    /** Drops the record of tokens that have expired on their own. */
    public int purgeExpired() {
        long now = System.currentTimeMillis();
        int before = revoked.size();
        revoked.values().removeIf(expiresAt -> expiresAt <= now);
        return before - revoked.size();
    }

    /** How many signed-out tokens are still being remembered. */
    public int size() {
        return revoked.size();
    }

    public long getValiditySeconds() {
        return validity.toSeconds();
    }

    private byte[] sign(String encoded) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign a token", e);
        }
    }

}
