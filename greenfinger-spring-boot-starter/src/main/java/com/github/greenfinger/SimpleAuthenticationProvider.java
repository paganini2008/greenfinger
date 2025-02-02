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
        throw new UsernameNotFoundException("Invalid username '" + username + "' or password");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

}
