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

import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.record.ResourceRecord;
import lombok.Getter;
import lombok.Setter;

/**
 * One page on its way through the outputs.
 *
 * <p>
 * {@code record} is what the database accepted, and it is the authority: ids, file paths and image
 * references all come from it, never from the page still in memory. {@code page} carries the bytes
 * the file layer has yet to write, and is null on a replay, where the files already exist and only
 * the index or the vectors are being rebuilt.
 * 
 * @Description: OutputPayload
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
public class OutputPayload {

    private final CatalogDetails catalogDetails;
    private final ResourceRecord record;

    /** Null on replay. */
    private final CrawledPage page;

    /** Read back from the file store after the file layer has written it. */
    private String text;

    private String html;

    public OutputPayload(CatalogDetails catalogDetails, ResourceRecord record, CrawledPage page) {
        this.catalogDetails = catalogDetails;
        this.record = record;
        this.page = page;
        if (page != null) {
            this.text = page.getText();
            this.html = page.getHtml();
        }
    }

    public boolean isReplay() {
        return page == null;
    }

    public String getUrl() {
        return record.resource().getUrl();
    }

}
