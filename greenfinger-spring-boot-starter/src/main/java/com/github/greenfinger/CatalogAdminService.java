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

package com.github.greenfinger;

import org.springframework.stereotype.Service;
import com.github.greenfinger.searcher.ResourceIndexManager;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: CatalogAdminService
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private final ResourceManager resourceManager;
    private final ResourceIndexManager resourceIndexManager;

    @WebCrawling
    public void cleanCatalog(long catalogId, boolean retainIndex) {
        resourceManager.deleteResourceByCatalogId(catalogId);
        if (!retainIndex) {
            resourceIndexManager.deleteResource(catalogId, -1);
            // resourceManager.updateCatalogIndex(new CatalogIndex(catalogId, 0, new Date()));
        }
    }

    @WebCrawling
    public void deleteCatalog(long catalogId, boolean retainIndex) {
        if (!retainIndex) {
            resourceIndexManager.deleteResource(catalogId, -1);
        }
        resourceManager.deleteResourceByCatalogId(catalogId);
        resourceManager.deleteCatalog(catalogId);

    }

}
