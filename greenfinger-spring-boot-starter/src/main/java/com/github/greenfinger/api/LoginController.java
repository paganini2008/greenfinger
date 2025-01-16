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
