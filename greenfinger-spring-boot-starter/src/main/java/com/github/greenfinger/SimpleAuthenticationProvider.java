package com.github.greenfinger;

import java.util.Collections;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.github.doodler.common.context.ServerProperties;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: SimpleAuthenticationProvider
 * @Author: Fred Feng
 * @Date: 16/01/2025
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class SimpleAuthenticationProvider implements AuthenticationProvider {

    private final ServerProperties serverProperties;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();
        if (serverProperties.getCredentials().getUser().equals(username)
                && serverProperties.getCredentials().getPassword().equals(password)) {
            return new UsernamePasswordAuthenticationToken(username, password,
                    Collections.singletonList(new SimpleGrantedAuthority("GREENFINGER_ADMIN")));
        }
        throw new UsernameNotFoundException("Invalid username: " + username);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

}
