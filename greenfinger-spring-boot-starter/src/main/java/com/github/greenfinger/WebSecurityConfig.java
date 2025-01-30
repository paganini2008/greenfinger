package com.github.greenfinger;

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
import com.github.doodler.common.context.ServerProperties;
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable().httpBasic().and().formLogin().disable().logout().disable().cors()
                .and().authorizeRequests().antMatchers("/monitor/**").permitAll()
                .antMatchers("/v1/login").permitAll().antMatchers("/v1/**").authenticated()
                .anyRequest().permitAll();
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
