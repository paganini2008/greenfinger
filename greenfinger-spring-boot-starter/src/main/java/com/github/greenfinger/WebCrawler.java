package com.github.greenfinger;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.InterruptionChecker;
import com.github.greenfinger.components.UrlPathAcceptor;
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
    private List<InterruptionChecker> interruptedConditions;

    @Autowired
    private List<UrlPathAcceptor> urlPathAcceptors;

    @Autowired
    private Extractor pageSourceExtractor;

}
