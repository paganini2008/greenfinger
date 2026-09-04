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

package com.github.greenfinger.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.doodler.common.context.ContextPath;
import com.github.doodler.common.context.ServerProperties;
import com.github.greenfinger.SimpleAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * 
 * @Description: WebSecurityConfig
 * @Author: Fred Feng
 * @Date: 16/01/2025
 * @Version 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private final ContextPath contextPath;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable().httpBasic().and().formLogin().disable().logout().disable().cors()
                .and().authorizeRequests()

                .antMatchers("/actuator/**", "/monitor/**").permitAll()
                .antMatchers(contextPath.getContextPath() + "/v1/login").permitAll()
                .antMatchers(contextPath.getContextPath() + "/v1/**").authenticated().anyRequest()
                .permitAll().and().exceptionHandling()
                .authenticationEntryPoint(new ForbiddenAuthenticationEntryPoint(objectMapper))
                .accessDeniedHandler(new GlobalAccessDeniedHandler(objectMapper));

    }

    @Bean
    public SimpleAuthenticationProvider simpleAuthenticationProvider(
            ServerProperties serverProperties) {
        return new SimpleAuthenticationProvider(serverProperties);
    }

    @SneakyThrows
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
            List<AuthenticationProvider> authenticationProviders) {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        for (AuthenticationProvider authenticationProvider : authenticationProviders) {
            authenticationManagerBuilder.authenticationProvider(authenticationProvider);
        }
        return authenticationManagerBuilder.build();
    }
}
