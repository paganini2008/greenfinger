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

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.github.paganini2008.devtools.CharsetUtils;
import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.collection.CollectionUtils;
import com.github.paganini2008.devtools.collection.MapUtils;
import com.github.paganini2008.devtools.io.LineIterator;
import com.github.paganini2008.devtools.io.PathUtils;
import com.github.paganini2008.devtools.net.Urls;

import io.atlantisframework.vortex.common.Tuple;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * RobotProtocolPathAcceptor
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
public class RobotProtocolPathAcceptor implements PathAcceptor {

	private static final String PATTERN_ALLOW = "Allow:|allow:";
	private static final String PATTERN_DISALLOW = "Disallow:|disallow:";

	private final Map<String, RobotFile> cache = new ConcurrentHashMap<String, RobotFile>();

	private final PathMatcher pathMather = new AntPathMatcher();

	private final PageExtractor pageExtractor;

	public RobotProtocolPathAcceptor(PageExtractor pageExtractor) {
		this.pageExtractor = pageExtractor;
	}

	@Override
	public boolean accept(long catalogId, String refer, String path, Tuple tuple) {
		RobotFile robotFile = MapUtils.get(cache, refer, () -> {
			return parseRobotFile(catalogId, refer, refer + "/robot.txt", tuple);
		});
		if (robotFile == null) {
			return true;
		}
		String thePath;
		try {
			thePath = Urls.toURL(path).getPath();
		} catch (RuntimeException ignored) {
			thePath = "";
		}
		String theExt = PathUtils.getExtension(path);
		if (CollectionUtils.isNotEmpty(robotFile.getAllow())) {
			for (String allowPath : robotFile.getAllow()) {
				if (allowPath.endsWith("$")) {
					String pattern = allowPath.substring(0, allowPath.length() - 1);
					if (pathMather.match(pattern, thePath) || theExt.equals(pattern)) {
						return true;
					}
				}
				if (thePath.startsWith(allowPath) || pathMather.match(allowPath, thePath)) {
					return true;
				}
			}
			return false;
		} else {
			for (String disallowPath : robotFile.getDisallow()) {
				if (disallowPath.endsWith("$")) {
					String pattern = disallowPath.substring(0, disallowPath.length() - 1);
					if (pathMather.match(pattern, thePath) || theExt.equals(pattern)) {
						return false;
					}
				}
				if (thePath.startsWith(disallowPath) || pathMather.match(disallowPath, thePath)) {
					return false;
				}
			}
			return true;
		}
	}

	protected RobotFile parseRobotFile(long catalogId, String refer, String path, Tuple tuple) {
		Charset pageEncoding = CharsetUtils.toCharset(tuple.getField("pageEncoding", String.class));
		String content;
		try {
			content = pageExtractor.extractHtml(refer, path, pageEncoding, tuple);
		} catch (Exception e) {
			content = "";
		}
		if (StringUtils.isBlank(content)) {
			return new RobotFile();
		}
		RobotFile robot = new RobotFile();
		LineIterator iterator = new LineIterator(new StringReader(content));
		String line;
		while (iterator.hasNext()) {
			line = iterator.next();
			if (line.startsWith("Allow") || line.startsWith("allow")) {
				line = line.replace(PATTERN_ALLOW, "").trim();
				robot.getDisallow().add(line);
			}
			if (line.startsWith("Disallow") || line.startsWith("disallow")) {
				line = line.replace(PATTERN_DISALLOW, "").trim();
				robot.getDisallow().add(line);
			}
		}
		iterator.close();
		return robot;
	}

	@Getter
	@Setter
	static class RobotFile {

		private String userAgent;
		private List<String> allow = new ArrayList<String>();
		private List<String> disallow = new ArrayList<String>();
	}

}
