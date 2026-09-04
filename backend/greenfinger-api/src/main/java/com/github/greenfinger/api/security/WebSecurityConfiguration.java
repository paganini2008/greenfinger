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

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.api.web.ApiResult;
import lombok.RequiredArgsConstructor;

/**
 * Who gets in, and what they may do once in.
 *
 * <p>
 * Two roles, and the line between them is reading against changing. {@code SUPPORT} may look at
 * everything -- catalogs, progress, search results -- and change nothing. {@code ADMIN} may also
 * create a catalog, start a crawl, update, rebuild and delete. That is the whole point of having
 * the second role: the search page can be handed to somebody without handing them the buttons that
 * start and destroy work.
 *
 * <p>
 * Stateless, because the front end is a single page application holding a bearer token. No
 * session means no CSRF token to carry and no cookie to get wrong across origins, which is why
 * both are disabled rather than merely unconfigured.
 *
 * @Description: WebSecurityConfiguration
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(GreenfingerSecurityProperties.class)
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    public static final String ROLE_ADMIN = "ADMIN";

    private final GreenfingerSecurityProperties securityProperties;

    @Value("${greenfinger.api.prefix:/v2}")
    private String apiPrefix;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public TokenStore tokenStore() {
        return new TokenStore(securityProperties.getTokenValidity(),
                securityProperties.getTokenSecret());
    }

    /**
     * The user directory: whatever {@code GF_USERS} listed, and nothing else. There is no path by
     * which an account appears at run time.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                PreAllocatedUsers.parse(securityProperties.getUsers()));
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Its own mapper rather than the application's. All it ever writes is a two-field failure
     * envelope, and taking a dependency on a bean would make the whole chain -- the thing that
     * decides who gets in -- fail to build if Jackson were configured differently.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenStore tokenStore)
            throws Exception {
        String prefix = apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix;
        http.csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new TokenAuthenticationFilter(tokenStore),
                        UsernamePasswordAuthenticationFilter.class);

        if (!securityProperties.isEnabled()) {
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(requests -> requests
                // signing in, and the health probe a container asks for, cannot require a token
                .requestMatchers(prefix + "/login", prefix + "/version", "/actuator/health/**",
                        "/error")
                .permitAll()
                // the single page application itself: the login form has to be servable
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/ui/**",
                        "/assets/**", "/*.js", "/*.css", "/*.webp", "/*.png", "/*.svg")
                .permitAll()
                // The cluster's own endpoints tell anybody who asks the node addresses, the
                // cluster name and where the leader is. Health stays open above because a probe
                // has no account; the rest needs one.
                .requestMatchers("/actuator/**").authenticated()
                // GET is reading, so SUPPORT passes here; everything else falls through to ADMIN
                .requestMatchers(HttpMethod.GET, prefix + "/**").authenticated()
                .requestMatchers(prefix + "/logout", prefix + "/me").authenticated()
                .requestMatchers(prefix + "/**").hasRole(ROLE_ADMIN).anyRequest().permitAll());

        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint(
                        (request, response, exception) -> write(response, 401, "Sign in first"))
                .accessDeniedHandler((request, response, exception) -> write(response, 403,
                        "Your account may read this, but not change it")));
        return http.build();
    }

    /**
     * The same envelope the controllers return. A front end that has one shape to parse should not
     * suddenly meet another one just because it was turned away at the door.
     */
    private void write(jakarta.servlet.http.HttpServletResponse response, int status,
            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResult.failed(message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(securityProperties.getCorsOrigins().split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        // patterns rather than exact origins so a port range or a wildcard host still works
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
