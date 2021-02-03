package org.springtribe.framework.webcrawler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springtribe.framework.greenfinger.es.IndexedResourceService;
import org.springtribe.framework.greenfinger.es.SearchResult;
import org.springtribe.framework.webcrawler.utils.PageBean;
import org.springtribe.framework.webcrawler.utils.Response;

import com.github.paganini2008.devtools.jdbc.PageResponse;

/**
 * 
 * SearchController
 *
 * @author Jimmy Hoff
 * @since 1.0
 */
@RequestMapping("/catalog/index")
@RestController
public class SearchController {

	@Autowired
	private IndexedResourceService indexedResourceService;

	@GetMapping("/search")
	public Response search(@RequestParam("q") String keyword,
			@RequestParam(name = "version", required = false, defaultValue = "0") int version,
			@RequestParam(value = "page", defaultValue = "1", required = false) int page,
			@CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size) {
		PageResponse<SearchResult> pageResponse = indexedResourceService.search(keyword, version, page, size);
		return Response.success(PageBean.wrap(pageResponse));
	}

}
