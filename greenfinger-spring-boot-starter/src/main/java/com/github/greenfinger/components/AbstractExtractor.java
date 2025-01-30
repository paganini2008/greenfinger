package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

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
    public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        String content = requestUrl(catalogDetails, referUrl, url, pageEncoding, packet);
        content = rewriteContent(catalogDetails, referUrl, url, pageEncoding, content);
        return content;
    }

    private List<ResponseBodyRewriter> responseBodyRewriters = new ArrayList<>();

    public void setResponseBodyRewriters(List<ResponseBodyRewriter> responseBodyRewriters) {
        this.responseBodyRewriters = responseBodyRewriters;
    }

    protected abstract String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception;

    protected String rewriteContent(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        if (CollectionUtils.isEmpty(responseBodyRewriters)) {
            return content;
        }
        for (ResponseBodyRewriter bodyRewriter : responseBodyRewriters) {
            content = bodyRewriter.rewrite(catalogDetails, referUrl, url, pageEncoding, content);
        }
        return content;
    }

}
