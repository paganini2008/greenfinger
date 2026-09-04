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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * The one line of configuration that is the whole user directory.
 *
 * @Description: PreAllocatedUsersTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
class PreAllocatedUsersTest {

    @Test
    @DisplayName("the two accounts the product ships with")
    void parsesTheDefaultPair() {
        List<UserDetails> users =
                PreAllocatedUsers.parse("admin:admin123:ADMIN,tester:tester123:SUPPORT");

        assertThat(users).hasSize(2);
        assertThat(users.get(0).getUsername()).isEqualTo("admin");
        assertThat(users.get(0).getPassword()).isEqualTo("{noop}admin123");
        assertThat(roles(users.get(0))).containsExactly("ROLE_ADMIN");
        assertThat(users.get(1).getUsername()).isEqualTo("tester");
        assertThat(roles(users.get(1))).containsExactly("ROLE_SUPPORT");
    }

    @Test
    @DisplayName("an account with no role stated may look, not touch")
    void defaultsToTheReadOnlyRole() {
        assertThat(roles(PreAllocatedUsers.parse("watcher:secret").get(0)))
                .containsExactly("ROLE_SUPPORT");
    }

    @Test
    void acceptsSeveralRolesOnOneAccount() {
        assertThat(roles(PreAllocatedUsers.parse("both:pw:ADMIN|SUPPORT").get(0)))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPPORT");
    }

    @Test
    void ignoresSpacingAndEmptyEntries() {
        assertThat(PreAllocatedUsers.parse(" admin:admin123:ADMIN , ,tester:tester123:SUPPORT "))
                .hasSize(2);
    }

    @Test
    @DisplayName("a mistyped account is refused at startup, not at the first login attempt")
    void refusesAnAccountWithoutAPassword() {
        assertThatThrownBy(() -> PreAllocatedUsers.parse("admin"))
                .isInstanceOf(WebCrawlerException.class)
                .hasMessageContaining("is not username:password:role");
        assertThatThrownBy(() -> PreAllocatedUsers.parse("admin::ADMIN"))
                .isInstanceOf(WebCrawlerException.class);
    }

    @Test
    void refusesAnEmptyDirectory() {
        assertThatThrownBy(() -> PreAllocatedUsers.parse("  "))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("GF_USERS");
        assertThatThrownBy(() -> PreAllocatedUsers.parse(",,"))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("GF_USERS");
    }

    private static List<String> roles(UserDetails user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

}
