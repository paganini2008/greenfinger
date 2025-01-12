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
