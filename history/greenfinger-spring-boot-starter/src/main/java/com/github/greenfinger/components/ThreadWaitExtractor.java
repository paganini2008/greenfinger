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

package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.doodler.common.context.BeanLifeCycleUtils;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: ThreadWaitExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class ThreadWaitExtractor implements Extractor, ManagedBeanLifeCycle {

    private final Extractor extractor;
    private final ThreadWait threadWait;

    public ThreadWaitExtractor(Extractor extractor, ThreadWait threadWait) {
        this.extractor = extractor;
        this.threadWait = threadWait;
    }

    @Override
    public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        if (catalogDetails.getFetchInterval() != null) {
            threadWait.doWait(catalogDetails.getFetchInterval());
        } else {
            ThreadWait.RANDOM_SLEEP.doWait(1000L);
        }
        return extractor.extractHtml(catalogDetails, referUrl, url, pageEncoding, packet);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
    }

    @Override
    public void destroy() throws Exception {
        BeanLifeCycleUtils.destroy(extractor);
    }

}
