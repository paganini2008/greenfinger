/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.api;

import java.util.Base64;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.greenfinger.api.pojo.LoginForm;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: LoginController
 * @Author: Fred Feng
 * @Date: 16/01/2025
 * @Version 1.0.0
 */
@RequestMapping("/v1")
@RestController
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authenticationManager;

    @PostMapping("/login")
    public ApiResult<String> login(@RequestBody LoginForm loginForm) {
        Authentication authentication =
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                        loginForm.getUsername(), loginForm.getPassword()));
        if (authentication != null) {
            String credentials = loginForm.getUsername() + ":" + loginForm.getPassword();
            String basicAuthHeader = Base64.getEncoder().encodeToString(credentials.getBytes());
            return ApiResult.ok(basicAuthHeader);
        }
        throw new UsernameNotFoundException("Invalid username: " + loginForm.getUsername());
    }

    @PostMapping("/logout")
    public ApiResult<String> logout() {
        return ApiResult.ok();
    }
}
