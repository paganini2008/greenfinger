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

import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.greenfinger.api.security.TokenAuthenticationFilter;
import com.github.greenfinger.api.security.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Signing in and out.
 *
 * <p>
 * Login returns a bearer token; every later call carries it in {@code Authorization}. Logout drops
 * the token from the store, which is what makes it a real sign-out rather than a hint the browser
 * is free to ignore.
 *
 * @Description: AuthApiController
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@Validated
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final TokenStore tokenStore;

    @PostMapping("/login")
    public ApiResult<Session> login(@Valid @RequestBody LoginForm loginForm) {
        // a wrong password lands here as an AuthenticationException, which the handler turns into
        // one 401 with one message: telling the caller which half was wrong helps only an attacker
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginForm.username(), loginForm.password()));
        UserDetails user = userDetailsService.loadUserByUsername(loginForm.username());
        return ApiResult.ok(sessionOf(tokenStore.issue(user), user.getUsername(),
                authorities(user.getAuthorities())));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        TokenAuthenticationFilter.tokenOf(request).ifPresent(tokenStore::revoke);
        SecurityContextHolder.clearContext();
        return ApiResult.ok();
    }

    /**
     * Who the held token belongs to. The front end calls this on a reload rather than trusting
     * what it kept in local storage, so a token revoked meanwhile fails at once.
     */
    @GetMapping("/me")
    public ApiResult<Session> me(HttpServletRequest request, Authentication authentication) {
        String token = TokenAuthenticationFilter.tokenOf(request).orElse(null);
        return ApiResult.ok(sessionOf(token, authentication.getName(),
                authorities(authentication.getAuthorities())));
    }

    private Session sessionOf(String token, String username, List<String> roles) {
        return new Session(token, username, roles, tokenStore.getValiditySeconds());
    }

    private static List<String> authorities(
            java.util.Collection<? extends GrantedAuthority> granted) {
        return granted.stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

}
