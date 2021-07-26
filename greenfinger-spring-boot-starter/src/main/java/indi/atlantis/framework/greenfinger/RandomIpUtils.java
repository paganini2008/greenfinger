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

import java.util.concurrent.ThreadLocalRandom;

/**
 * 
 * RandomIpUtils
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public abstract class RandomIpUtils {

	private static final int[][] range = { { 607649792, 608174079 }, { 1038614528, 1039007743 }, { 1783627776, 1784676351 },
			{ 2035023872, 2035154943 }, { 2078801920, 2079064063 }, { -1950089216, -1948778497 }, { -1425539072, -1425014785 },
			{ -1236271104, -1235419137 }, { -770113536, -768606209 }, { -569376768, -564133889 }, };

	public static String randomIp() {
		int index = ThreadLocalRandom.current().nextInt(range.length);
		int ip = range[index][0] + ThreadLocalRandom.current().nextInt(range[index][1] - range[index][0]);
		int[] b = new int[4];
		b[0] = (int) ((ip >> 24) & 0xff);
		b[1] = (int) ((ip >> 16) & 0xff);
		b[2] = (int) ((ip >> 8) & 0xff);
		b[3] = (int) (ip & 0xff);
		return Integer.toString(b[0]) + "." + Integer.toString(b[1]) + "." + Integer.toString(b[2]) + "." + Integer.toString(b[3]);
	}

	public static void main(String[] args) {
		for (int i = 0; i < 10000; i++) {
			System.out.println(randomIp());
		}
	}

}
