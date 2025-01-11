package com.github.greenfinger.components.test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: AbstractExtractor
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public abstract class AbstractExtractor implements Extractor {

    protected Map<String, String> defaultHttpHeaders = new HashMap<>() {

        private static final long serialVersionUID = 1L;
        {
            put("Accept", "*/*");
            put("Accept-Language", "en-US,en;q=0.9");
        }
    };

    public Map<String, String> getDefaultHttpHeaders() {
        return defaultHttpHeaders;
    }

    public void setDefaultHttpHeaders(Map<String, String> defaultHttpHeaders) {
        this.defaultHttpHeaders = defaultHttpHeaders;
    }

    @Override
    public String extractHtml(Catalog catalog, String referUrl, String url, Charset pageEncoding,
            Packet packet) throws Exception {
        String content = requestUrl(catalog, referUrl, url, pageEncoding, packet);
        return rewriteContent(content);
    }

    private List<ResponseBodyRewriter> responseBodyRewriters = new ArrayList<>();

    public void setResponseBodyRewriters(List<ResponseBodyRewriter> responseBodyRewriters) {
        this.responseBodyRewriters = responseBodyRewriters;
    }

    protected abstract String requestUrl(Catalog catalog, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception;

    protected String rewriteContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        if (CollectionUtils.isEmpty(responseBodyRewriters)) {
            return content;
        }
        for (ResponseBodyRewriter bodyRewriter : responseBodyRewriters) {
            content = bodyRewriter.rewrite(content);
        }
        return content;
    }

    @Override
    public String getDescription() {
        return getClass().getSimpleName();
    }

}
