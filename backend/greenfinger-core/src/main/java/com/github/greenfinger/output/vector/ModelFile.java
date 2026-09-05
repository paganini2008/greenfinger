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

package com.github.greenfinger.output.vector;

/**
 * One weight file a local model is made of.
 *
 * <p>
 * It exists so that "which files does the local provider need" is written down once. The client
 * loads them and {@code models pull} fetches them ahead of time; if the two kept their own lists,
 * a pull could report success having downloaded a file the client no longer opens.
 *
 * @param repository a Hugging Face repository, such as {@code Xenova/multilingual-e5-small}
 * @param file the path within it
 * @param tower {@code text} or {@code image} -- a text-only crawl never needs the larger pair
 *
 * @Description: ModelFile
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
public record ModelFile(String repository, String file, String tower) {

    public static final String TEXT = "text";
    public static final String IMAGE = "image";

    public boolean isText() {
        return TEXT.equals(tower);
    }

    public boolean isImage() {
        return IMAGE.equals(tower);
    }

    /** {@code Xenova/multilingual-e5-small/tokenizer.json}, for a table or a log line. */
    public String describe() {
        return repository + "/" + file;
    }

}
