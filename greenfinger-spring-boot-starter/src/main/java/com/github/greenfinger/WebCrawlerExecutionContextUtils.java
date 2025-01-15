package com.github.greenfinger;

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

    public static synchronized WebCrawlerExecutionContext get(long catalogId) {
        return MapUtils.getOrCreate(cache, catalogId, () -> {
            return ApplicationContextUtils.getBean(WebCrawlerExecutionContext.class);
        });
    }

    public static void remove(long catalogId) {
        WebCrawlerExecutionContext crawlerExecutionContext = cache.remove(catalogId);
        if (crawlerExecutionContext != null) {
            try {
                crawlerExecutionContext.destroy();
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
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
