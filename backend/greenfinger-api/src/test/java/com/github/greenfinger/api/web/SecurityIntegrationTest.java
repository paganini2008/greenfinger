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

package com.github.greenfinger.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The door: who gets in, and what they may do once in.
 *
 * <p>
 * Runs through the real filter chain rather than against the controllers, because everything worth
 * asserting here -- the 401, the 403, the token surviving from one request to the next -- happens
 * in the chain and not in a controller.
 *
 * @Description: SecurityIntegrationTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-security/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-security/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-security/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-security/content",
        "greenfinger.security.users=admin:admin123:ADMIN,tester:tester123:SUPPORT",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-security;DB_CLOSE_DELAY=-1"})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("the admin account signs in and is handed a token")
    void signsInAsAdmin() throws Exception {
        String body = mockMvc
                .perform(post("/v2/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin", "admin123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.expiresInSeconds").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenOf(body)).isNotBlank();
    }

    @Test
    @DisplayName("a wrong password is refused, and is not told which half was wrong")
    void refusesAWrongPassword() throws Exception {
        mockMvc.perform(post("/v2/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("admin", "not-the-password"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Wrong username or password"));

        mockMvc.perform(post("/v2/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("nobody", "whatever"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong username or password"));
    }

    @Test
    void refusesALoginWithNothingInIt() throws Exception {
        mockMvc.perform(post("/v2/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("", ""))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("required")));
    }

    @Test
    @DisplayName("without a token every endpoint is closed, and says so in the usual envelope")
    void turnsAwayAnUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/v2/catalog")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sign in first"));
        mockMvc.perform(get("/v2/catalog").header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the version is readable without signing in, so a wrong server can be spotted")
    void tellsAnyoneWhichVersionThisIs() throws Exception {
        mockMvc.perform(get("/v2/version")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Greenfinger"))
                .andExpect(jsonPath("$.data.version").isNotEmpty());
    }

    @Test
    void carriesTheTokenFromOneRequestToTheNext() throws Exception {
        String token = signIn("admin", "admin123");

        mockMvc.perform(get("/v2/catalog").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/v2/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @DisplayName("logging out actually revokes: the same token stops working immediately")
    void signsOut() throws Exception {
        String token = signIn("admin", "admin123");

        mockMvc.perform(post("/v2/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/v2/catalog").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the tester account reads everything")
    void letsSupportRead() throws Exception {
        String token = signIn("tester", "tester123");

        mockMvc.perform(get("/v2/catalog").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v2/crawl/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v2/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_SUPPORT"));
    }

    @Test
    @DisplayName("and changes nothing: creating a catalog, starting a crawl and deleting are shut")
    void refusesSupportEverythingThatWrites() throws Exception {
        String token = signIn("tester", "tester123");

        mockMvc.perform(post("/v2/catalog").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(
                post("/v2/crawl/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/v2/catalog/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String signIn(String username, String password) throws Exception {
        return tokenOf(mockMvc
                .perform(post("/v2/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String tokenOf(String responseBody) throws Exception {
        JsonNode node = objectMapper.readTree(responseBody);
        return node.path("data").path("token").asText();
    }

    private String json(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginForm(username, password));
    }

}
