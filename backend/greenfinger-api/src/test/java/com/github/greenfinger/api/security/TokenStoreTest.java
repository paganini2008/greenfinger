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

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Tokens that any node can check.
 *
 * @Description: TokenStoreTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class TokenStoreTest {

    private static final String SECRET = "a-shared-secret-for-the-test";

    private final UserDetails admin =
            User.withUsername("admin").password("{noop}admin123").roles("ADMIN").build();

    private TokenStore store(Duration validity) {
        return new TokenStore(validity, SECRET);
    }

    @Test
    void resolvesTheTokenItIssued() {
        TokenStore store = store(Duration.ofMinutes(30));
        String token = store.issue(admin);
        assertThat(store.resolve(token)).get().extracting(UserDetails::getUsername)
                .isEqualTo("admin");
        assertThat(store.getValiditySeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("a second node with the same secret accepts it, which is the point")
    void anotherNodeAcceptsIt() {
        String token = store(Duration.ofMinutes(30)).issue(admin);

        TokenStore elsewhere = new TokenStore(Duration.ofMinutes(30), SECRET);
        assertThat(elsewhere.resolve(token)).get().extracting(UserDetails::getUsername)
                .isEqualTo("admin");
        assertThat(elsewhere.resolve(token).orElseThrow().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a node with a different secret does not, so a stolen format is not a key")
    void anotherSecretRejectsIt() {
        String token = store(Duration.ofMinutes(30)).issue(admin);

        assertThat(new TokenStore(Duration.ofMinutes(30), "a-different-secret").resolve(token))
                .isEmpty();
    }

    @Test
    @DisplayName("a token whose payload was edited no longer verifies")
    void tamperingIsRejected() {
        TokenStore store = store(Duration.ofMinutes(30));
        String token = store.issue(admin);
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("root\n" + (System.currentTimeMillis() + 60_000)
                        + "\nROLE_ADMIN").getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + token.substring(token.lastIndexOf('.'));

        assertThat(store.resolve(forged)).isEmpty();
    }

    @Test
    @DisplayName("signing out is what makes a token stop working on this node")
    void revokesATokenOnLogout() {
        TokenStore store = store(Duration.ofMinutes(30));
        String token = store.issue(admin);
        store.revoke(token);
        assertThat(store.resolve(token)).isEmpty();
    }

    @Test
    @DisplayName("two browsers get two tokens, so signing out of one leaves the other alone")
    void issuesOneTokenPerLogin() throws Exception {
        TokenStore store = store(Duration.ofMinutes(30));
        String first = store.issue(admin);
        // the expiry is part of what is signed, so two issued in the same millisecond match
        Thread.sleep(2L);
        String second = store.issue(admin);

        assertThat(first).isNotEqualTo(second);
        store.revoke(first);
        assertThat(store.resolve(first)).isEmpty();
        assertThat(store.resolve(second)).isPresent();
    }

    @Test
    void forgetsAnExpiredToken() {
        TokenStore store = store(Duration.ZERO);
        String token = store.issue(admin);
        assertThat(store.resolve(token)).isEmpty();
        // nothing was kept in the first place, so an expired token costs no memory
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("what is remembered is only the sign-outs, and only until they would expire")
    void purgesWhatItRemembers() {
        TokenStore expiring = store(Duration.ofMillis(1));
        String token = expiring.issue(admin);
        expiring.revoke(token);

        TokenStore live = store(Duration.ofMinutes(30));
        String liveToken = live.issue(admin);
        live.revoke(liveToken);
        assertThat(live.size()).isEqualTo(1);
        assertThat(live.purgeExpired()).isZero();
    }

    @Test
    void treatsAnUnknownTokenAsNobody() {
        TokenStore store = store(Duration.ofMinutes(30));
        assertThat(store.resolve(null)).isEmpty();
        assertThat(store.resolve("not-a-token")).isEmpty();
        assertThat(store.resolve("no-dot-here")).isEmpty();
        assertThat(store.resolve("trailing.")).isEmpty();
        assertThat(store.resolve("!!!.!!!")).isEmpty();
        store.revoke(null);
        store.revoke("not-a-token");
    }

    @Test
    @DisplayName("without a configured secret it still works, for one process")
    void aGeneratedSecretStillSigns() {
        TokenStore store = new TokenStore(Duration.ofMinutes(30), null);
        String token = store.issue(admin);

        assertThat(store.resolve(token)).isPresent();
        // and a second process generates a different one, which is the warning's whole point
        assertThat(new TokenStore(Duration.ofMinutes(30), null).resolve(token)).isEmpty();
    }
}
