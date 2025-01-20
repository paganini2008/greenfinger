package com.github.greenfinger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.doodler.common.cloud.ApplicationInfo;
import com.github.doodler.common.events.GlobalApplicationEvent;

/**
 * 
 * @Description: WebCrawlerNewJoinerEvent
 * @Author: Fred Feng
 * @Date: 15/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerNewJoinerEvent extends GlobalApplicationEvent {

    public WebCrawlerNewJoinerEvent(ApplicationInfo applicationInfo) {
        super(applicationInfo);
    }

    @JsonIgnore
    @Override
    public String getName() {
        return "WebCrawlerNewJoiner";
    }

    @JsonCreator
    public static WebCrawlerNewJoinerEvent create(@JsonProperty("source") Object source) {
        return new WebCrawlerNewJoinerEvent((ApplicationInfo) source);
    }

}
