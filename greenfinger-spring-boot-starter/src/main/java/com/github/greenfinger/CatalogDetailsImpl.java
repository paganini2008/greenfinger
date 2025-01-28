package com.github.greenfinger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.doodler.common.utils.JacksonUtils;
import com.github.greenfinger.components.CountingType;
import com.github.greenfinger.components.ThreadWait;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogCredential;
import com.github.greenfinger.model.CatalogIndex;
import lombok.ToString;

/**
 * 
 * @Description: CatalogDetailsImpl
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
@ToString(onlyExplicitlyIncluded = true)
public class CatalogDetailsImpl implements CatalogDetails {

    public CatalogDetailsImpl(Catalog catalog, CatalogIndex catalogIndex,
            List<CatalogCredential> catalogCredentials, WebCrawlerProperties webCrawlerProperties) {
        this.catalog = catalog;
        this.catalogIndex = catalogIndex;
        this.pathPatterns = StringUtils.isNotBlank(catalog.getPathPattern())
                ? List.of(catalog.getPathPattern().split(","))
                : Collections.emptyList();
        this.excludedPathPatterns = StringUtils.isNotBlank(catalog.getExcludedPathPattern())
                ? List.of(catalog.getExcludedPathPattern().split(","))
                : Collections.emptyList();
        this.urlPathAcceptors = StringUtils.isNotBlank(catalog.getUrlPathAcceptor())
                ? List.of(catalog.getUrlPathAcceptor().split(","))
                : Collections.emptyList();
        this.catalogCredentials =
                Optional.ofNullable(catalogCredentials).filter(l -> !l.isEmpty())
                        .map(l -> l.stream().map(c -> new CatalogCredentialsImpl(c))
                                .toArray(n -> new CatalogCredentials[n]))
                        .orElse(new CatalogCredentials[0]);
        this.webCrawlerProperties = webCrawlerProperties;
    }

    private final Catalog catalog;
    private final CatalogIndex catalogIndex;
    private final WebCrawlerProperties webCrawlerProperties;
    private final List<String> pathPatterns;
    private final List<String> excludedPathPatterns;
    private final List<String> urlPathAcceptors;
    private final CatalogCredentials[] catalogCredentials;

    @ToString.Include
    @Override
    public Long getId() {
        return catalog.getId();
    }

    public String getDisplayId() {
        return catalog.getDisplayId();
    }

    @ToString.Include
    @Override
    public String getName() {
        return catalog.getName();
    }

    @ToString.Include
    @Override
    public String getUrl() {
        return catalog.getUrl();
    }

    @ToString.Include
    @Override
    public String getCategory() {
        return catalog.getCat();
    }

    @Override
    public String getStartUrl() {
        return catalog.getStartUrl();
    }

    @Override
    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    @Override
    public List<String> getExcludedPathPatterns() {
        return excludedPathPatterns;
    }

    @Override
    public String getPageEncoding() {
        return StringUtils.isNotBlank(catalog.getPageEncoding()) ? catalog.getPageEncoding()
                : webCrawlerProperties.getDefaultPageEncoding();
    }

    @Override
    public Integer getMaxFetchSize() {
        return Optional.ofNullable(catalog.getMaxFetchSize())
                .orElse(webCrawlerProperties.getDefaultMaxFetchSize());
    }

    @Override
    public Integer getMaxFetchDepth() {
        return Optional.ofNullable(catalog.getDepth())
                .orElse(webCrawlerProperties.getDefaultMaxFetchDepth());
    }

    @Override
    public ThreadWait getThreadWait() {
        return catalog.getInterval() != null ? ThreadWait.SLEEP : ThreadWait.RANDOM_SLEEP;
    }

    @Override
    public Long getFetchInterval() {
        return Optional.ofNullable(catalog.getInterval())
                .orElse(webCrawlerProperties.getDefaultFetchInterval());
    }

    @Override
    public Long getFetchDuration() {
        return Optional.ofNullable(catalog.getDuration())
                .orElse(webCrawlerProperties.getDefaultFetchDuration());
    }

    @Override
    public CountingType getCountingType() {
        return Optional.ofNullable(catalog.getCountingType()).orElse(CountingType.URL_TOTAL_COUNT);
    }

    @Override
    public Integer getMaxRetryCount() {
        return Optional.ofNullable(catalog.getMaxRetryCount())
                .orElse(webCrawlerProperties.getDefaultMaxRetryCount());
    }

    @Override
    public List<String> getUrlPathAcceptors() {
        return urlPathAcceptors;
    }

    @Override
    public String getUrlPathFilter() {
        return StringUtils.isNotBlank(catalog.getUrlPathFilter()) ? catalog.getUrlPathFilter()
                : webCrawlerProperties.getDefaultUrlPathFilter();
    }

    @Override
    public String getExtractor() {
        return StringUtils.isNotBlank(catalog.getExtractor()) ? catalog.getExtractor()
                : webCrawlerProperties.getDefaultExtractor();
    }

    @Override
    public String getCredentialHandler() {
        return catalog.getCredentialHandler();
    }

    @Override
    public Integer getVersion() {
        return catalogIndex.getVersion();
    }

    @Override
    public String getRunningState() {
        return catalog.getRunningState();
    }

    @Override
    public Boolean getIndexed() {
        return catalog.getIndexed();
    }

    @Override
    public CatalogCredentials[] getCatalogCredentials() {
        return catalogCredentials;
    }

    /**
     * 
     * @Description: CatalogCredentialsImpl
     * @Author: Fred Feng
     * @Date: 12/01/2025
     * @Version 1.0.0
     */
    @ToString(onlyExplicitlyIncluded = true)
    static class CatalogCredentialsImpl implements CatalogCredentials {

        private final CatalogCredential catalogCredential;
        private final Map<String, String> additionalInformation;

        CatalogCredentialsImpl(CatalogCredential catalogCredential) {
            this.catalogCredential = catalogCredential;
            this.additionalInformation =
                    JacksonUtils.parseJson(catalogCredential.getAdditionalInformation(),
                            new TypeReference<HashMap<String, String>>() {});
        }

        @ToString.Include
        @Override
        public String getUsername() {
            return catalogCredential.getUsername();
        }

        @ToString.Include
        @Override
        public String getPassword() {
            return catalogCredential.getPassword();
        }

        @Override
        public Map<String, String> getAdditionalInformation() {
            return additionalInformation;
        }

    }

}
