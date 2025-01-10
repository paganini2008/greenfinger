package com.github.greenfinger.components;

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
    public String extractHtml(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        long interval;
        try {
            interval = packet.getLongField("interval");
        } catch (RuntimeException e) {
            interval = 0;
        }
        if (interval > 0) {
            threadWait.doWait(interval);
        }
        return extractor.extractHtml(refer, url, pageEncoding, packet);
    }

    @Override
    public String getName() {
        return extractor.getName();
    }

}
