/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.cache.interceptor;

import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.core.util.ArrayUtils;

/**
 * A default implementation of the {@link CacheKeyGenerator} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultCacheKeyGenerator implements CacheKeyGenerator {
    @Override
    public Object generateKey(MethodInvocationContext invocationContext) {
        Object[] params = invocationContext.getParameterValues();
        if (ArrayUtils.isEmpty(params)) {
            return MethodParameterKey.ZERO_ARG_KEY;
        }
        if (params.length == 1) {
            Object param = params[0];
            if (param != null && !param.getClass().isArray()) {
                return param;
            }
            else {
                return new MethodParameterKey(params);
            }
        }
        else {
            return new MethodParameterKey(params);
        }
    }
}
