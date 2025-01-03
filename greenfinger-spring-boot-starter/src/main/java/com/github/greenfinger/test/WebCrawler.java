package com.github.greenfinger.test;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.greenfinger.UrlPathAcceptor;
import com.github.greenfinger.utils.PageSourceExtractor;
import lombok.Getter;

/**
 * 
 * @Description: WebCrawler
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Getter
public class WebCrawler {

    @Autowired
    private List<InterruptibleCondition> interruptedConditions;

    @Autowired
    private List<UrlPathAcceptor> urlPathAcceptors;

    @Autowired
    private PageSourceExtractor pageSourceExtractor;

}
