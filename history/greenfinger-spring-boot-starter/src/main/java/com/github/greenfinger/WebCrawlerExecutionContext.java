/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.GlobalStateManager;
import com.github.greenfinger.components.InterruptionChecker;
import com.github.greenfinger.components.UrlPathAcceptor;

/**
 * 
 * @Description: WebCrawlerExecutionContext
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
public interface WebCrawlerExecutionContext extends ManagedBeanLifeCycle {

    CatalogDetails getCatalogDetails();

    List<InterruptionChecker> getInterruptionCheckers();

    List<UrlPathAcceptor> getUrlPathAcceptors();

    Extractor getExtractor();

    ExistingUrlPathFilter getExistingUrlPathFilter();

    GlobalStateManager getGlobalStateManager();

    AtomicInteger getConcurrents();

    void waitForTermination(long timeout, TimeUnit timeUnit) throws Exception;

    boolean isUrlAcceptable(String referUrl, String path, Packet packet);

    boolean isCompleted();

    boolean shouldInterrupt();

}
