package indi.atlantis.framework.greenfinger;

import indi.atlantis.framework.reditools.common.RedisBloomFilter;

/**
 * 
 * BloomFilterPathFilter
 *
 * @author Jimmy Hoff
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
