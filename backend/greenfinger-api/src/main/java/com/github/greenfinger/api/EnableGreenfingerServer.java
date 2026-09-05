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

package com.github.greenfinger.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import com.github.greenfinger.service.EnableGreenfingerCrawler;
import com.github.greenfinger.api.web.GreenfingerWebConfiguration;

/**
 * The crawler, with its http face attached.
 *
 * <p>
 * Everything {@link EnableGreenfingerCrawler} provides, plus the REST endpoints -- which are added
 * only when there is a web application to add them to, so this annotation is safe on a plain one.
 *
 * <p>
 * An application that wants to drive the crawler from its own code and expose nothing should use
 * {@code @EnableGreenfingerCrawler} instead, and needs only greenfinger-core.
 * 
 * @Description: EnableGreenfingerServer
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@EnableGreenfingerCrawler
@Import(GreenfingerWebConfiguration.class)
public @interface EnableGreenfingerServer {

}
