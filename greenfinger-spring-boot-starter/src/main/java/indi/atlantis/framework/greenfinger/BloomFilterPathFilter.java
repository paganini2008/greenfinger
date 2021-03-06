/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

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

import com.github.paganini2008.springdessert.reditools.common.RedisBloomFilter;

/**
 * 
 * BloomFilterPathFilter
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public class BloomFilterPathFilter implements PathFilter {

	private final RedisBloomFilter bloomFilter;

	public BloomFilterPathFilter(RedisBloomFilter bloomFilter) {
		this.bloomFilter = bloomFilter;
	}

	@Override
	public void update(String content) {
		if (!mightExist(content)) {
			bloomFilter.put(content);
		}
	}

	@Override
	public boolean mightExist(String content) {
		return bloomFilter.mightContain(content);
	}

}
