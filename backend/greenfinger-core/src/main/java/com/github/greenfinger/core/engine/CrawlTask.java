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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One url waiting to be fetched.
 *
 * <p>
 * This replaces the 1.x {@code Packet}, a string-keyed map shuttled over the network. Making it a
 * typed object removes the casts that littered the handler, and it carries a real {@code depth}
 * rather than leaving the depth check to count slashes in the url.
 *
 * <p>
 * It is also what one node sends another, so it is a wire format and has to behave like one.
 * Unknown fields are ignored: nodes are upgraded one at a time, and for the length of that window
 * a node running the new build sends tasks to nodes running the old one. Without this, a field
 * added in the new version would make every url it sends undecodable on the others -- and the
 * receiving channel drops what it cannot read, so the symptom would be pages quietly going
 * missing during an upgrade and nowhere else.
 * 
 * @Description: CrawlTask
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrawlTask implements Serializable {

    private static final long serialVersionUID = 4677871104449251923L;

    public static final String ACTION_CRAWL = "crawl";
    public static final String ACTION_UPDATE = "update";

    private String catalogId;
    private String action = ACTION_CRAWL;

    /** The catalog's own url, the origin every link is judged against. */
    private String referUrl;

    /** The page this task was linked from; null for the seed. */
    private String referer;

    private String url;
    private String cat;
    private String pageEncoding;
    private int version;

    /** Links traversed from the seed to reach this url. The seed is zero. */
    private int depth;

    /** When the task was created, used to keep the rolling average execution time honest. */
    private long timestamp = System.currentTimeMillis();

    /**
     * Position in the frontier. Assigned on the way out of the frontier, and the handle used to
     * remove the task once it completes.
     */
    private transient long sequence;

    public static CrawlTask seed(String catalogId, String action, String referUrl, String url,
            String cat, String pageEncoding, int version) {
        CrawlTask task = new CrawlTask();
        task.setCatalogId(catalogId);
        task.setAction(action);
        task.setReferUrl(referUrl);
        task.setUrl(url);
        task.setCat(cat);
        task.setPageEncoding(pageEncoding);
        task.setVersion(version);
        task.setDepth(0);
        return task;
    }

    /**
     * A task for a link found on this page, one level deeper.
     */
    public CrawlTask child(String childUrl) {
        CrawlTask task = new CrawlTask();
        task.setCatalogId(catalogId);
        task.setAction(action);
        task.setReferUrl(referUrl);
        task.setReferer(url);
        task.setUrl(childUrl);
        task.setCat(cat);
        task.setPageEncoding(pageEncoding);
        task.setVersion(version);
        task.setDepth(depth + 1);
        return task;
    }

}
