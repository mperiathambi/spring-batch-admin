/*
 * Copyright 2006-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.admin.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Dave Syer
 * 
 */
public class SimpleEhCacheInterceptor implements MethodInterceptor, InitializingBean, DisposableBean {

	private final Cache cache = new Cache("simple", 0, true, false, 60, 0);

	private final CacheManager manager = CacheManager.create();

	public void afterPropertiesSet() throws Exception {
		manager.addCache(cache);
	}

	public void destroy() throws Exception {
		manager.removalAll();
		manager.shutdown();
	}

	public Object invoke(MethodInvocation invocation) throws Throwable {
		Serializable key = getKey(invocation);
		Element element = cache.get(key);
		Object value = null;
		if (element == null || element.isExpired()) {
			cache.remove(key);
			Object old = element == null ? null : element.getValue();
			value = invocation.proceed();
			if (cacheable(value, old)) {
				cache.putIfAbsent(new Element(key, value));
			}
		}
		else {
			value = element.getObjectValue();
		}
		return value;
	}

	@SuppressWarnings("rawtypes")
	private boolean cacheable(Object value, Object old) {
		if (value == null) {
			return false;
		}
		if (old != null) {
			return true;
		}
		if (value instanceof Collection) {
			if (((Collection) value).isEmpty()) {
				return false;
			}

		}
		if (value instanceof Map) {
			if (((Map) value).isEmpty()) {
				return false;
			}
		}
		if (value.getClass().isArray()) {
			if (((Object[])value).length==0) {
				return false;
			}
		}
		return true;
	}

	private Serializable getKey(MethodInvocation invocation) {
		return invocation.getMethod().getName() + Arrays.asList(invocation.getArguments());
	}

}