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

package com.github.greenfinger.core.record;

import java.util.List;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;

/**
 * What the database holds for one crawled page, read back before anything downstream is written.
 *
 * <p>
 * The index and the vector store are built from this rather than from the {@code CrawledPage} still
 * sitting in memory, so that replaying a layer later works from exactly the same input and produces
 * exactly the same result. The body itself is not in here -- the database keeps metadata only --
 * and is read from the path in {@code htmlContentFilePath}.
 * 
 * @Description: ResourceRecord
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public record ResourceRecord(Resource resource, List<ImageRecord> images) {

    /**
     * One image together with how this page referenced it. Both halves are needed downstream: the
     * image carries the file and its dimensions, the reference carries the alt text and the
     * surrounding words that make it findable.
     */
    public record ImageRecord(Image image, ResourceImage reference) {
    }

}
