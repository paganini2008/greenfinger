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

package com.github.greenfinger;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Somewhere for the Spring test slices to start from.
 *
 * <p>
 * This module is a library and has no application class of its own, so {@code @DataJpaTest} has
 * nothing to search upwards for -- and it searches upwards only, never sideways. It sits at
 * {@code com.github.greenfinger} rather than in the cluster package for a second reason: the
 * repositories a row writer needs live under {@code com.github.greenfinger.record}, and a slice
 * scans downwards from here.
 *
 * <p>
 * Empty on purpose: what each slice actually needs, it imports.
 * 
 * @Description: TestBoot
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@SpringBootApplication
public class TestBoot {

}
