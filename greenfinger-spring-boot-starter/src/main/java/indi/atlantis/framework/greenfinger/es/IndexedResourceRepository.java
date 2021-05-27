package indi.atlantis.framework.greenfinger.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Component;

/**
 * 
 * IndexedResourceRepository
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Component
public interface IndexedResourceRepository extends ElasticsearchRepository<IndexedResource, Long> {

}
