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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Who may sign in, and for how long.
 *
 * <p>
 * The accounts are handed out up front and written into the configuration -- there is no
 * registration, no user table and no password reset, because greenfinger is an operator's tool
 * rather than a public site. One line in {@code .env} is the whole user directory.
 *
 * <p>
 * {@link #users} is a single string rather than a list of objects for exactly that reason:
 * environment variables have no way to express a list, and every value in this project comes from
 * {@code .env}.
 *
 * @Description: GreenfingerSecurityProperties
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "greenfinger.security")
public class GreenfingerSecurityProperties {

    /** Turning this off leaves every endpoint open. Only ever right behind a private network. */
    private boolean enabled = true;

    /**
     * The pre-allocated accounts, as {@code username:password:role[|role]}, separated by commas.
     */
    private String users = "admin:admin123:ADMIN,tester:tester123:SUPPORT";

    /** How long a token stays valid without being used. Each request renews it. */
    private Duration tokenValidity = Duration.ofHours(8);

    /**
     * The key sign-in tokens are signed with. Share it across every node, or each will reject the
     * others' tokens and a browser behind a load balancer will be asked to sign in again on every
     * other request.
     *
     * <p>
     * Deliberately without a default: a shipped default would be a published key. Blank means one
     * is generated for this process and a warning is logged.
     */
    private String tokenSecret;

    /** Browser origins allowed to call the api. The Angular dev server is the usual one. */
    private String corsOrigins = "http://localhost:4200";

}
