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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * Turns the one configured line of accounts into what Spring Security expects.
 *
 * <p>
 * Kept apart from the configuration class so the parsing -- the part that can be got wrong -- can
 * be tested without starting a context.
 *
 * @Description: PreAllocatedUsers
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
public abstract class PreAllocatedUsers {

    /**
     * Parses {@code username:password:role[|role], ...}.
     *
     * <p>
     * The password is stored as {@code {noop}} because it arrived in plain text: hashing it here
     * would only hide from the reader that whoever can read the configuration already knows it.
     * The honest place to keep it secret is the file itself, which is why it lives in {@code .env}
     * and never in the repository.
     */
    public static List<UserDetails> parse(String configured) {
        if (StringUtils.isBlank(configured)) {
            throw new WebCrawlerException("No account is configured: set GF_USERS in .env,"
                    + " as username:password:ADMIN");
        }
        List<UserDetails> users = new ArrayList<>();
        for (String entry : configured.split(",")) {
            if (StringUtils.isBlank(entry)) {
                continue;
            }
            String[] parts = entry.trim().split(":");
            if (parts.length < 2 || StringUtils.isAnyBlank(parts[0], parts[1])) {
                throw new WebCrawlerException("Account '" + entry.trim()
                        + "' is not username:password:role");
            }
            String[] roles = parts.length > 2 && StringUtils.isNotBlank(parts[2])
                    ? parts[2].split("\\|")
                    : new String[] {"SUPPORT"};
            users.add(User.withUsername(parts[0].trim()).password("{noop}" + parts[1].trim())
                    .roles(roles).build());
        }
        if (users.isEmpty()) {
            throw new WebCrawlerException("No account is configured: set GF_USERS in .env,"
                    + " as username:password:ADMIN");
        }
        return users;
    }

}
