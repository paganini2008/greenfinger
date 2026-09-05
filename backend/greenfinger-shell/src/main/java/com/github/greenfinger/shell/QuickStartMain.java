/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.shell;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.CrawlerLauncher;

/**
 * An entry point for running a crawl from an IDE, without the shell in the way.
 *
 * <p>
 * It takes the same path everything else does -- save the catalog, then run what was saved -- so
 * what happens here is what happens on the command line.
 * 
 * @Description: QuickStartMain
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class QuickStartMain {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "https://books.toscrape.com";
        int maxSize = args.length > 1 ? Integer.parseInt(args[1]) : 1000;

        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(GreenfingerShellMain.class)
                        .web(WebApplicationType.NONE)
                        .properties("spring.shell.interactive.enabled=false")
                        .run()) {

            CatalogAdminService catalogAdminService = context.getBean(CatalogAdminService.class);
            CrawlerLauncher launcher = context.getBean(CrawlerLauncher.class);
            CatalogDetailsService catalogDetailsService =
                    context.getBean(CatalogDetailsService.class);

            Catalog catalog = new Catalog();
            catalog.setUrl(url);
            catalog.setMaxFetchSize(maxSize);
            catalog.setOutputTypes(java.util.Set.of(OutputType.FILE));
            catalog = catalogAdminService.save(catalog);

            var result = launcher.crawl(catalog.getId(), null);
            System.out.println("Saved " + result.getDashboard().getSavedResourceCount()
                    + " page(s), " + result.getDashboard().getSavedImageCount() + " image(s) of "
                    + catalogDetailsService.loadCatalogDetails(catalog.getId()).getName());
        }
    }

}
