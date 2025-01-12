package com.github.greenfinger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import com.github.doodler.common.context.ApplicationContextUtils;
import com.github.doodler.common.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerExecutionContextUtils
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Component
public class WebCrawlerExecutionContextUtils implements DisposableBean {

    private static final Map<Long, WebCrawlerExecutionContext> cache = new ConcurrentHashMap<>();

    public static WebCrawlerExecutionContext createFrom(CatalogDetails catalogDetails) {
        return MapUtils.getOrCreate(cache, catalogDetails.getId(), () -> {
            WebCrawlerExecutionContext context =
                    new DefaultWebCrawlerExecutionContext(catalogDetails);
            return ApplicationContextUtils.autowireBean(context);
        });
    }

    public static Collection<Long> getActiveIds() {
        return Collections.unmodifiableCollection(cache.keySet());
    }

    public static WebCrawlerExecutionContext get(long catalogId) {
        return cache.get(catalogId);
    }

    public static WebCrawlerExecutionContext remove(long catalogId) {
        return cache.remove(catalogId);
    }

    public static int getActiveCount() {
        return cache.size();
    }

    @Override
    public void destroy() throws Exception {
        cache.values().forEach(c -> {
            if (c instanceof DisposableBean) {
                try {
                    ((DisposableBean) c).destroy();
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        });
    }

}
