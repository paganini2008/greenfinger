package org.springtribe.framework.webcrawler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springtribe.framework.greenfinger.es.IndexedResourceService;
import org.springtribe.framework.webcrawler.utils.Response;

import com.github.paganini2008.devtools.multithreads.ThreadUtils;

/**
 * 
 * IndexController
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@RequestMapping("/catalog/index")
@RestController
public class IndexController {

	@Autowired
	private IndexedResourceService indexedResourceService;

	@GetMapping("/{id}")
	public Response indexAll(@PathVariable("id") Long catalogId) {
		ThreadUtils.runAsThread(() -> {
			indexedResourceService.indexAll(catalogId, false);
		});
		return Response.success("Submit OK.");
	}

	@GetMapping("/{id}/delete")
	public Response deleteResource(@PathVariable("id") Long catalogId) {
		indexedResourceService.deleteResource(catalogId, 0);
		return Response.success("Submit OK.");
	}

}
