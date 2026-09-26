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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

/**
 * A rest endpoint, and the switch that leaves it out.
 *
 * <p>
 * An installation with no front end wants nodes that crawl and gossip and nothing on the http port
 * but health: no api to expose, no login to configure. The controllers are component-scanned, so
 * the condition has to be on each of them rather than on the configuration that imports them.
 *
 * @Description: ApiEndpoint
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@ConditionalOnProperty(prefix = "greenfinger.api.web", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public @interface ApiEndpoint {

}
