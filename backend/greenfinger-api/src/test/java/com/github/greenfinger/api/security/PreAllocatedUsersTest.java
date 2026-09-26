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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * The file that is the whole user directory.
 *
 * @Description: PreAllocatedUsersTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
class PreAllocatedUsersTest {

    @Test
    @DisplayName("the two accounts the product ships with")
    void readsTheDefaultPair() {
        List<UserDetails> users = parse("""
                <users>
                  <user name="admin" password="admin123" roles="ADMIN"/>
                  <user name="tester" password="tester123" roles="SUPPORT"/>
                </users>
                """);

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
        assertThat(roles(parse("<users><user name=\"watcher\" password=\"pw\"/></users>").get(0)))
                .containsExactly("ROLE_SUPPORT");
    }

    @Test
    @DisplayName("one account can hold both roles, with either separator")
    void acceptsSeveralRolesOnOneAccount() {
        assertThat(roles(
                parse("<users><user name=\"a\" password=\"p\" roles=\"ADMIN,SUPPORT\"/></users>")
                        .get(0))).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPPORT");
        assertThat(roles(
                parse("<users><user name=\"a\" password=\"p\" roles=\"ADMIN|SUPPORT\"/></users>")
                        .get(0))).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPPORT");
    }

    @Test
    @DisplayName("a mistyped account is refused at startup, not at the first login attempt")
    void refusesAnAccountWithoutAPassword() {
        assertThatThrownBy(() -> parse("<users><user name=\"admin\"/></users>"))
                .isInstanceOf(WebCrawlerException.class)
                .hasMessageContaining("no name or no password");
    }

    @Test
    void refusesAFileWithNobodyInIt() {
        assertThatThrownBy(() -> parse("<users/>")).isInstanceOf(WebCrawlerException.class)
                .hasMessageContaining("No account in");
        assertThatThrownBy(() -> parse("not xml at all"))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("not readable as xml");
    }

    @Test
    @DisplayName("a document that declares an entity is refused rather than expanded")
    void refusesADoctype() {
        // the file is read before anybody has signed in, so it is read with doctypes off
        assertThatThrownBy(() -> parse("""
                <!DOCTYPE users [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <users><user name="a" password="&secret;"/></users>
                """)).isInstanceOf(WebCrawlerException.class);
    }

    @Test
    @DisplayName("no file is a warning and the shipped accounts, not a node that will not start")
    void aMissingFileStillStarts(@TempDir Path directory) {
        List<UserDetails> users = PreAllocatedUsers.load(directory.resolve("nothing.xml").toString());
        assertThat(users).extracting(UserDetails::getUsername).containsExactly("admin", "tester");
    }

    @Test
    void readsTheFileItIsPointedAt(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("users.xml");
        Files.writeString(file, "<users><user name=\"someone\" password=\"pw\" roles=\"ADMIN\"/>"
                + "</users>");
        assertThat(PreAllocatedUsers.load(file.toString())).extracting(UserDetails::getUsername)
                .containsExactly("someone");
    }

    @Test
    @DisplayName("the file the build ships parses, and holds one of each role")
    void theShippedFileIsValid() throws Exception {
        Path file = Path.of("src/main/resources/config/users.xml");
        try (InputStream in = Files.newInputStream(file)) {
            List<UserDetails> users = PreAllocatedUsers.parse(in, file.toString());
            assertThat(users).hasSize(2);
            assertThat(users).anySatisfy(
                    user -> assertThat(roles(user)).containsExactly("ROLE_ADMIN"));
            assertThat(users).anySatisfy(
                    user -> assertThat(roles(user)).containsExactly("ROLE_SUPPORT"));
        }
    }

    private static List<UserDetails> parse(String xml) {
        return PreAllocatedUsers.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "users.xml");
    }

    private static List<String> roles(UserDetails user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

}
