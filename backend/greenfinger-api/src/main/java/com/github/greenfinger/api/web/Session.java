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

/**
 * What the front end is told after a successful sign in: the token to send back, who it belongs
 * to, and what that account may do -- the last so the page can hide the buttons it would only be
 * refused on anyway.
 *
 * @Description: Session
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
public record Session(String token, String username, List<String> roles, long expiresInSeconds) {

    public boolean admin() {
        return roles.contains("ROLE_ADMIN");
    }

}
