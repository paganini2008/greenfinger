/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.components;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.paganini2008.devtools.Sequence;
import com.github.paganini2008.devtools.multithreads.latch.CounterLatch;
import com.github.paganini2008.devtools.multithreads.latch.Latch;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestMain {

    public static void main(String[] args) throws Exception {
        System.out.println(FileUtils.getTempDirectoryPath());
        Latch latch = new CounterLatch(10);
        SeleniumExtractor extractor = new SeleniumExtractor(new WebCrawlerExtractorProperties());
        extractor.afterPropertiesSet();
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        latch.forEach(Sequence.forEach(1, 100), executorService, i -> {
            try {
                String str = extractor.test("https://www.javaoneworld.com/p/tech-products.html",
                        StandardCharsets.UTF_8);
                System.out.println(str);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });
        System.out.println("TestMain.main()1");
        System.in.read();
        System.out.println("TestMain.main()2");
        executorService.shutdown();
        System.out.println("TestMain.main()3");
        extractor.destroy();
    }

}
