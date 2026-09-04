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

package com.github.greenfinger.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Turns the crawler on: the engine, the outputs, persistence and the services that drive them.
 *
 * <p>
 * This is the whole crawler and nothing to do with http. The command line uses it directly; a web
 * application uses {@code @EnableGreenfingerServer} from the starter, which is this plus the REST
 * endpoints.
 *
 * <p>
 * Deliberately explicit rather than automatic: merely having the jar on the classpath should not
 * start opening RocksDB stores and holding a crawl semaphore.
 * 
 * @Description: EnableGreenfingerCrawler
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(GreenfingerConfiguration.class)
public @interface EnableGreenfingerCrawler {

}
