package indi.atlantis.framework.greenfinger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.collection.CollectionUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * DefaultPathAcceptor
 *
 * @author Fred Feng
 * @since 1.0
 */
public class DefaultPathAcceptor implements PathAcceptor {

	private final PathMatcher pathMather = new AntPathMatcher();

	@Autowired
	private ResourceManager resourceManager;

	private final Map<Long, List<String>> pathPatternCache = new ConcurrentHashMap<Long, List<String>>();
	private final Map<Long, List<String>> excludedPathPatternCache = new ConcurrentHashMap<Long, List<String>>();

	@SuppressWarnings("unchecked")
	@Override
	public boolean accept(long catalogId, String refer, String path, Tuple tuple) {
		List<String> pathPatterns = MapUtils.get(excludedPathPatternCache, catalogId, () -> {
			Catalog catalog = resourceManager.getCatalog(catalogId);
			if (StringUtils.isBlank(catalog.getExcludedPathPattern())) {
				return Collections.EMPTY_LIST;
			}
			return Arrays.asList(catalog.getExcludedPathPattern().split(","));
		});
		for (String pathPattern : pathPatterns) {
			if (pathMather.match(pathPattern, path)) {
				return false;
			}
		}

		pathPatterns = MapUtils.get(pathPatternCache, catalogId, () -> {
			Catalog catalog = resourceManager.getCatalog(catalogId);
			if (StringUtils.isBlank(catalog.getPathPattern())) {
				return Collections.EMPTY_LIST;
			}
			return Arrays.asList(catalog.getPathPattern().split(","));
		});

		if (CollectionUtils.isEmpty(pathPatterns)) {
			return path.startsWith(refer);
		}
		for (String pathPattern : pathPatterns) {
			if (pathMather.match(pathPattern, path)) {
				return true;
			}
		}
		return false;
	}

	public static void main(String[] args) {
		PathMatcher pathMather = new AntPathMatcher();
		final String pattern = "https://**.tuniu.**/**";
		System.out.println(pathMather.match(pattern, "https://sina.tuniu.com/a/b"));
	}

}
