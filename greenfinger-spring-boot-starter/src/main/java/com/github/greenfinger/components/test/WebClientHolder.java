package com.github.greenfinger.components.test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.github.doodler.common.utils.Coookie;

/**
 * 
 * @Description: WebClientHolder
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface WebClientHolder<T> extends Supplier<T> {

    T createNew();

    default void setExtraHttpHeaders(Map<String, String> headerMap) {}

    default void setExtraHttpHeader(String headerName, String headerValue) {
        setExtraHttpHeaders(Map.of(headerName, headerValue));
    }

    default void setExtraCookies(String domain, Map<String, String> cookieMap) {
        List<Coookie> coookies = cookieMap.entrySet().stream().collect(ArrayList::new,
                (l, e) -> l.add(new Coookie(domain, e.getKey(), e.getValue())), ArrayList::addAll);
        setExtraCookies(coookies);
    }

    default void setExtraCookie(String domain, String cookieName, String cookieValue) {
        setExtraCookies(domain, Map.of(cookieName, cookieValue));
    }

    default void setExtraCookies(List<Coookie> coookies) {}

    default String test(String url, Charset pageEncoding) throws Exception {
        throw new UnsupportedOperationException("test: " + url);
    }

}
