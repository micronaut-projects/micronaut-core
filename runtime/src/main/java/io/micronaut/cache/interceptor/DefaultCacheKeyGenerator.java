/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cache.interceptor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.ArrayUtils;

/**
 * <p>A default implementation of the {@link CacheKeyGenerator} interface that uses the parameters of the method only.</p>
 * <p>
 * <p>This implementation is appropriate for most common cases but note that collisions can occur for classes that
 * use the same cache and have overlapping signatures as the default implementation does not use the method itself
 * when generating the key</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Introspected
public class DefaultCacheKeyGenerator implements CacheKeyGenerator {

    @Override
    public Object generateKey(AnnotationMetadata annotationMetadata, Object... params) {
        if (ArrayUtils.isEmpty(params)) {
            return ParametersKey.ZERO_ARG_KEY;
        }
        if (params.length == 1) {
            Object param = params[0];
            if (param != null && !param.getClass().isArray()) {
                return param;
            } else {
                return new ParametersKey(params);
            }
        } else {
            return new ParametersKey(params);
        }
    }
}
