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

import org.springframework.context.ApplicationEvent;
import lombok.Getter;

/**
 * A crawl is over, published on every node. The shared counters already carry the fact; what they
 * do not carry is a moment, and polling a flag arrives late by the interval.
 *
 * <p>
 * The node winding the run down announces it once, and every node publishes this locally exactly
 * once -- which is what makes {@code @EventListener} mean what a reader expects.
 *
 * <p>
 * A notification, not a decision: nothing waits for a listener and one that throws is logged and
 * stepped over, because the version is already published by the time this is sent.
 * 
 * @Description: WebCrawlerCompletionEvent
 * @Author: Fred Feng
 * @Date: 04/09/2026
 * @Version 2.0.0
 */
@Getter
public class WebCrawlerCompletionEvent extends ApplicationEvent {

    private static final long serialVersionUID = 7943712925193875821L;

    private final String catalogId;

    /** The version the run was writing, published by now unless it was interrupted. */
    private final int version;

    /** What ended it, in the words the dashboard and the report use. */
    private final String reason;

    /** True when the run was cut short rather than reaching a limit of its own. */
    private final boolean interrupted;

    public WebCrawlerCompletionEvent(Object source, String catalogId, int version, String reason,
            boolean interrupted) {
        super(source);
        this.catalogId = catalogId;
        this.version = version;
        this.reason = reason;
        this.interrupted = interrupted;
    }
}
