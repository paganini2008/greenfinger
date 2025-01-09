package com.github.greenfinger.utils;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;

/**
 * 
 * @Description: ThreadWaitExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class ThreadWaitExtractor implements Extractor {

    private final Extractor pageExtractor;
    private final ThreadWait threadWait;

    public ThreadWaitExtractor(Extractor pageExtractor) {
        this(pageExtractor, ThreadWait.RANDOM_SLEEP);
    }

    public ThreadWaitExtractor(Extractor pageExtractor, ThreadWait threadWait) {
        this.pageExtractor = pageExtractor;
        this.threadWait = threadWait;
    }

    @Override
    public String extractHtml(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        long interval = (Long) packet.getField("interval", 100L);
        if (interval > 0) {
            threadWait.doWait(interval);
        }
        return pageExtractor.extractHtml(refer, url, pageEncoding, packet);
    }

}
