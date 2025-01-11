package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;

/**
 * 
 * @Description: CompositeExtractor
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public class CompositeExtractor implements Extractor, ManagedBeanLifeCycle {

    private final Map<String, NamedExetractor> extractors = new ConcurrentHashMap<>();

    public CompositeExtractor(Collection<NamedExetractor> extractors) {
        Optional.ofNullable(extractors).ifPresent(c -> {
            c.forEach(e -> addExtractor(e));
        });
    }

    public void addExtractor(NamedExetractor extractor) {
        Assert.notNull(extractor, "Extractor must not be null.");
        extractors.put(extractor.getName(), extractor);
    }

    public void removeExtractor(NamedExetractor extractor) {
        Assert.notNull(extractor, "Extractor must not be null.");
        extractors.remove(extractor.getName(), extractor);
    }

    @Override
    public String extractHtml(String referUrl, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        String extractorName = (String) packet.getField("extractor", "default");
        Extractor extractor = extractors.get(extractorName);
        return extractor.extractHtml(referUrl, url, pageEncoding, packet);
    }

    @Override
    public String defaultHtml(String referUrl, String url, Charset pageEncoding, Packet packet,
            Throwable e) {
        String extractorName = (String) packet.getField("extractor", "default");
        Extractor extractor = extractors.get(extractorName);
        return extractor.defaultHtml(referUrl, url, pageEncoding, packet, e);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        for (Extractor extractor : extractors.values()) {
            if (extractor instanceof InitializingBean) {
                ((InitializingBean) extractor).afterPropertiesSet();
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        for (Extractor extractor : extractors.values()) {
            if (extractor instanceof DisposableBean) {
                ((DisposableBean) extractor).destroy();
            }
        }
    }

}
