/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.core.io.scan;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>Extended version of {@link ClassPathAnnotationScanner} that temporarily caches the result of scan.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CachingClassPathAnnotationScanner extends ClassPathAnnotationScanner {

    private static final int CACHE_MAX = 5;
    private final Cache<CacheKey, List<Class>> initializedObjectsByType = Caffeine.newBuilder()
                                                                                  .maximumSize(CACHE_MAX)
                                                                                  .expireAfterAccess(2, TimeUnit.MINUTES)
                                                                                  .build();

    /**
     * Constructor.
     *
     * @param classLoader classLoader
     */
    public CachingClassPathAnnotationScanner(ClassLoader classLoader) {
        super(classLoader);
    }

    /**
     * Default Constructor.
     */
    public CachingClassPathAnnotationScanner() {
    }

    @Override
    protected List<Class> doScan(String annotation, String pkg) {
        return initializedObjectsByType.get(new CacheKey(annotation, pkg), (key) -> super.doScan(annotation, pkg));
    }

    /**
     * Inner class CacheKey.
     */
    private final class CacheKey implements Serializable {
        final String annotation;
        final String pkg;

        public CacheKey(String annotation, String pkg) {
            this.annotation = annotation;
            this.pkg = pkg;
        }
    }
}
