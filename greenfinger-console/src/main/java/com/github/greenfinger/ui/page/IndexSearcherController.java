package com.github.greenfinger.ui.page;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.doodler.common.jdbc.page.PageBean;
import com.github.doodler.common.jdbc.page.PageResponse;
import com.github.greenfinger.es.ResourceIndexService;
import com.github.greenfinger.es.SearchResult;

/**
 * 
 * IndexSearcherController
 *
 * @author Fred Feng
 * @since 2.0.1
 */
@RequestMapping("/index/searcher")
@Controller
public class IndexSearcherController {

    @Autowired
    private ResourceIndexService resourceIndexService;

    @GetMapping("/")
    public String index(Model ui) {
        return "searcher/search";
    }

    @PostMapping("/search")
    public String search(@RequestParam("q") String keyword,
            @RequestParam(name = "cat", required = false) String cat,
            @RequestParam(name = "version", required = false) Integer version,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size,
            Model ui) throws Exception {
        PageResponse<SearchResult> pageResponse =
                resourceIndexService.search(cat, keyword, version, page, size);
        PageBean<SearchResult> pageBean = PageBean.wrap(pageResponse);
        ui.addAttribute("page", pageBean);
        return "searcher/search_result";
    }

}
