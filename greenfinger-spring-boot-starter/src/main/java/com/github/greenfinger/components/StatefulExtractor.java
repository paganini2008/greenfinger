package com.github.greenfinger.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import com.github.doodler.common.utils.Coookie;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: StatefulExtractor
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public abstract class StatefulExtractor<T> extends AbstractExtractor
        implements ExetractorLifeCycle, NamedExetractor, WebClientHolder<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final AtomicBoolean logged = new AtomicBoolean(false);
    private final ExtractorCredentialHandler<T> credentialHandler;

    protected StatefulExtractor(ExtractorCredentialHandler<T> credentialHandler) {
        this.credentialHandler = credentialHandler;
    }

    protected List<Coookie> coookies = new ArrayList<>();

    @Override
    public void setExtraHttpHeaders(Map<String, String> headerMap) {
        Assert.notNull(headerMap, "HttpHeader map must not be null.");
        defaultHttpHeaders.putAll(headerMap);
    }

    @Override
    public void setExtraCookies(List<Coookie> coookies) {
        Assert.notNull(coookies, "Cookie list must not be null.");
        coookies.addAll(coookies);
    }

    @Override
    public boolean hasLogged(CatalogDetails catalogDetails) {
        return logged.get();
    }

    protected final void setLogged(boolean logged) {
        this.logged.set(logged);
    }

    @Override
    public void login(CatalogDetails catalogDetails) {
        if (hasLogged(catalogDetails)) {
            return;
        }
        credentialHandler.login(catalogDetails, this);
        setLogged(true);
    }

    @Override
    public void logout(CatalogDetails catalogDetails) {
        if (hasLogged(catalogDetails)) {
            credentialHandler.logout(catalogDetails, this);
            setLogged(false);
        }
    }

}
