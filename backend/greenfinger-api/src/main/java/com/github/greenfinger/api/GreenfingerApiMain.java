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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The server.
 *
 * <p>
 * The same crawler the command line runs, with an http face and a login in front of it. The
 * annotation rather than an auto-configuration entry is deliberate: an application that embeds
 * this module decides for itself when the endpoints appear.
 *
 * @Description: GreenfingerApiMain
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@SpringBootApplication
@EnableGreenfingerServer
public class GreenfingerApiMain {

    public static void main(String[] args) {
        SpringApplication.run(GreenfingerApiMain.class, args);
    }

}
