/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.atlantisframework.greenfinger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.collection.CollectionUtils;
import com.github.paganini2008.devtools.collection.ListUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

import io.atlantisframework.greenfinger.model.Catalog;
import io.atlantisframework.vortex.common.Tuple;

/**
 * 
 * DefaultPathAcceptor
 *
 * @author Fred Feng
 * @since 2.0.1
 */
public class DefaultPathAcceptor implements PathAcceptor, Ordered {

	private final PathMatcher pathMather = new AntPathMatcher();

	@Autowired
	private ResourceManager resourceManager;

	private final Map<Long, List<String>> pathPatternCache = new ConcurrentHashMap<Long, List<String>>();
	private final Map<Long, List<String>> excludedPathPatternCache = new ConcurrentHashMap<Long, List<String>>();

	@Override
	public boolean accept(long catalogId, String refer, String path, Tuple tuple) {
		List<String> pathPatterns = MapUtils.get(excludedPathPatternCache, catalogId, () -> {
			Catalog catalog = resourceManager.getCatalog(catalogId);
			if (StringUtils.isBlank(catalog.getExcludedPathPattern())) {
				return ListUtils.emptyList();
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
				return ListUtils.emptyList();
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

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 100;
	}

	public static void main(String[] args) {
		PathMatcher pathMather = new AntPathMatcher();
		final String pattern = "https://**.tuniu.**/**";
		System.out.println(pathMather.match(pattern, "https://sina.tuniu.com/a/b"));
	}

}
