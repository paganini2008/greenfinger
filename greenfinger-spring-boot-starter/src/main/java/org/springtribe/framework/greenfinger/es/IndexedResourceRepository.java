package org.springtribe.framework.greenfinger.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Component;

/**
 * 
 * IndexedResourceRepository
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Component
public interface IndexedResourceRepository extends ElasticsearchRepository<IndexedResource, Long> {

}
