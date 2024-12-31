package com.github.greenfinger.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Component;

/**
 * 
 * @Description: IndexedResourceRepository
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Component
public interface IndexedResourceRepository extends ElasticsearchRepository<IndexedResource, Long> {

}
