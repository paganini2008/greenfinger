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

package com.github.greenfinger.record;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.github.greenfinger.core.model.ResourceImage;

/**
 * 
 * @Description: ResourceImageRepository
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface ResourceImageRepository extends JpaRepository<ResourceImage, String> {

    List<ResourceImage> findByResourceId(String resourceId);

    List<ResourceImage> findByResourceIdIn(List<String> resourceIds);

    @Modifying
    @Query("delete from ResourceImage ri where ri.catalogId = ?1 and ri.version = ?2")
    int deleteByCatalogIdAndVersion(String catalogId, int version);

    int deleteByCatalogId(String catalogId);

}
