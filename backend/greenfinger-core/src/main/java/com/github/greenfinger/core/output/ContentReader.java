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

package com.github.greenfinger.core.output;

import java.util.Optional;

/**
 * Reads back what the file layer wrote, so the index and the vector store are built from persisted
 * bytes rather than from the objects still in memory. Replaying a layer then works from exactly the
 * same input and produces exactly the same result.
 * 
 * @Description: ContentReader
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface ContentReader {

    Optional<String> readText(String path) throws Exception;

    Optional<byte[]> readBytes(String path) throws Exception;

}
