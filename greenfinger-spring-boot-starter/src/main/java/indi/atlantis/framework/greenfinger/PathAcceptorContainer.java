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
package indi.atlantis.framework.greenfinger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import com.github.paganini2008.devtools.collection.CollectionUtils;
import com.github.paganini2008.devtools.collection.MultiListMap;

import indi.atlantis.framework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * PathAcceptorContainer
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
@Slf4j
public class PathAcceptorContainer implements BeanPostProcessor {

	private final List<PathAcceptor> pathAcceptors = new CopyOnWriteArrayList<PathAcceptor>();
	private final MultiListMap<Long, PathAcceptor> catalogPathAcceptors = new MultiListMap<Long, PathAcceptor>();

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof PathAcceptor) {
			pathAcceptors.add((PathAcceptor) bean);
		}
		return bean;
	}

	public void addPathAcceptor(long catalogId, PathAcceptor pathAcceptor) {
		if (pathAcceptor != null) {
			catalogPathAcceptors.add(catalogId, pathAcceptor);
		}
	}

	public void setPathAcceptor(long catalogId, PathAcceptor pathAcceptor) {
		if (pathAcceptor != null) {
			catalogPathAcceptors.put(catalogId, Collections.singletonList(pathAcceptor));
		} else {
			catalogPathAcceptors.remove(catalogId);
		}
	}

	public boolean acceptAll(long catalogId, String refer, String path, Tuple tuple) {
		for (PathAcceptor pathAcceptor : pathAcceptors) {
			if (pathAcceptor.accept(catalogId, refer, path, tuple) == false) {
				return false;
			}
		}
		List<PathAcceptor> acceptors = catalogPathAcceptors.get(catalogId);
		if (CollectionUtils.isNotEmpty(acceptors)) {
			try {
				for (PathAcceptor pathAcceptor : acceptors) {
					if (pathAcceptor.accept(catalogId, refer, path, tuple) == false) {
						return false;
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				return false;
			}
		}
		return true;
	}

}
