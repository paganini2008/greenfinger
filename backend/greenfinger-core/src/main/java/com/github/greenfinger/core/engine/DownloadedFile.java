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

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file a page linked to: the handbook as a pdf, the price list as a spreadsheet.
 *
 * <p>
 * Collected while the page is parsed and carried on {@link CrawledPage}. Nothing fetches one yet
 * -- what reads a fetched one is
 * {@link com.github.greenfinger.core.document.DocumentContentParser}, and only txt and markdown
 * have an implementation that ships.
 *
 * @Description: DownloadedFile
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadedFile implements Serializable {

    /** Travels between nodes and into the replication channel, so it is serializable. */
    private static final long serialVersionUID = 7041558220914432003L;

    private String url;

    /** Lower case, without the dot: {@code pdf}, {@code xlsx}. Taken from the path, never a query. */
    private String fileType;

    /** The words somebody clicks to get it, which is usually what the file is called. */
    private String linkText;

    /** Whether this installation has a parser for it. False is not a failure, it is a decision. */
    private boolean readable;

    public DownloadedFile(String url, String fileType, String linkText) {
        this.url = url;
        this.fileType = fileType;
        this.linkText = linkText;
    }

}
