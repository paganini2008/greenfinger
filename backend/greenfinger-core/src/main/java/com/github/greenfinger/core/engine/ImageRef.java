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

package com.github.greenfinger.core.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An image referenced by a page, before anything has been downloaded. Width and height are whatever
 * the markup declared, which is a hint rather than a fact -- the real dimensions are read from the
 * bytes once fetched.
 * 
 * @Description: ImageRef
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageRef {

    private String url;
    private String alt;

    /** The tag's own title attribute, which is often filled in where alt is not. */
    private String title;

    /**
     * The wording around the tag, truncated. An image carries no searchable text of its own and its
     * alt attribute is frequently empty, so the surrounding copy is what makes it findable -- the
     * principle image search has always run on.
     */
    private String context;

    private Integer declaredWidth;
    private Integer declaredHeight;

    /** Where on the page it was found: img, srcset or meta. */
    private String source;

    public ImageRef(String url, String source) {
        this.url = url;
        this.source = source;
    }

}
