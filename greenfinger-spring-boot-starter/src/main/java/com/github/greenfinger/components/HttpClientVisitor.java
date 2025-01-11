package com.github.greenfinger.components;

import org.openqa.selenium.WebDriver;
import org.springframework.web.client.RestTemplate;
import com.gargoylesoftware.htmlunit.WebClient;
import com.microsoft.playwright.BrowserContext;

/**
 * 
 * @Description: HttpClientVisitor
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface HttpClientVisitor {

    String visit(RestTemplate restTemplate);

    String visit(WebClient webClient);

    String visit(BrowserContext browserContext);

    String visit(WebDriver webDriver);

}
