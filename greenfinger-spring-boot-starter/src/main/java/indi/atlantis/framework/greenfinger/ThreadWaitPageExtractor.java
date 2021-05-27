package indi.atlantis.framework.greenfinger;

import java.nio.charset.Charset;

import com.github.paganini2008.devtools.RandomUtils;
import com.github.paganini2008.devtools.multithreads.ThreadUtils;

/**
 * 
 * ThreadWaitPageExtractor
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public class ThreadWaitPageExtractor implements PageExtractor {

	private final PageExtractor pageExtractor;
	private final long delay;
	
	public ThreadWaitPageExtractor(PageExtractor pageExtractor) {
		this(pageExtractor, 3000);
	}

	public ThreadWaitPageExtractor(PageExtractor pageExtractor, long delay) {
		this.pageExtractor = pageExtractor;
		this.delay = delay;
	}

	@Override
	public String extractHtml(String refer, String url, Charset pageEncoding) throws Exception {
		ThreadUtils.sleep(delay + RandomUtils.randomLong(100, 1000));
		return pageExtractor.extractHtml(refer, url, pageEncoding);
	}

}
