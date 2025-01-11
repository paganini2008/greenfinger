package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: ThreadWaitExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class ThreadWaitExtractor implements Extractor {

    private final Extractor extractor;
    private final ThreadWait threadWait;

    public ThreadWaitExtractor(Extractor extractor) {
        this(extractor, ThreadWait.RANDOM_SLEEP);
    }

    public ThreadWaitExtractor(Extractor extractor, ThreadWait threadWait) {
        this.extractor = extractor;
        this.threadWait = threadWait;
    }

    @Override
    public String extractHtml(Catalog catalog, String referUrl, String url, Charset pageEncoding,
            Packet packet) throws Exception {
        threadWait.doWait(catalog.getInterval());
        return extractor.extractHtml(catalog, referUrl, url, pageEncoding, packet);
    }

}
