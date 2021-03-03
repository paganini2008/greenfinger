package indi.atlantis.framework.greenfinger;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import indi.atlantis.framework.tridenter.utils.BeanLifeCycle;

/**
 * 
 * PageExtractorSupport
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public abstract class PageExtractorSupport<T> extends BasePooledObjectFactory<T> implements BeanLifeCycle {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected ObjectPool<T> objectPool;
	protected PageExtractorPoolConfig<T> poolConfig = new PageExtractorPoolConfig<T>();

	public PageExtractorPoolConfig<T> getPoolConfig() {
		return poolConfig;
	}

	public void setPoolConfig(PageExtractorPoolConfig<T> poolConfig) {
		this.poolConfig = poolConfig;
	}

	@Override
	public T create() throws Exception {
		return createObject();
	}

	@Override
	public PooledObject<T> wrap(T object) {
		return new DefaultPooledObject<T>(object);
	}

	public abstract T createObject() throws Exception;

	@Override
	public void configure() throws Exception {
		objectPool = new GenericObjectPool<T>(this, poolConfig);
	}

	@Override
	public void destroy() {
		if (objectPool != null) {
			objectPool.close();
		}
	}

	public static class PageExtractorPoolConfig<T> extends GenericObjectPoolConfig<T> {
		PageExtractorPoolConfig() {
			setMinIdle(1);
			setMaxIdle(5);
			setMaxTotal(20);
		}
	}

}
