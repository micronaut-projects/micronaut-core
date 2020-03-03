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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An interface that provides basic bean introspection. Designed as a simpler replacement for {@link java.beans.Introspector}.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link BeanIntrospector}
 */
@Internal
@Deprecated
public final class Introspector {

    /* The cache to store Bean Info objects that have been found or created */

    @SuppressWarnings({"unchecked", "ConstantName"})
    private static final Map<Class<?>, BeanInfo> theCache = new ConcurrentHashMap<>();

    private Introspector() {
        super();
    }

    /**
     * Flushes all <code>BeanInfo</code> caches.
     */
    public static void flushCaches() {
        // Flush the cache by throwing away the cache HashMap and creating a
        // new empty one
        theCache.clear();
    }

    /**
     * Flushes the <code>BeanInfo</code> caches of the specified bean class.
     *
     * @param clazz the specified bean class
     */
    public static void flushFromCaches(Class<?> clazz) {
        if (clazz == null) {
            throw new NullPointerException();
        }
        theCache.remove(clazz);
    }

    /**
     * Gets the <code>BeanInfo</code> object which contains the information of
     * the properties, events and methods of the specified bean class.
     * <p>
     * The <code>Introspector</code> will cache the <code>BeanInfo</code>
     * object. Subsequent calls to this method will be answered with the cached
     * data.
     * </p>
     *
     * @param <T>       type Generic
     * @param beanClass the specified bean class.
     * @return the <code>BeanInfo</code> of the bean class.
     */
    @SuppressWarnings("unchecked")
    static <T> BeanInfo<T> getBeanInfo(Class<T> beanClass) {
        BeanInfo beanInfo = theCache.get(beanClass);
        if (beanInfo == null) {
            beanInfo = new SimpleBeanInfo(beanClass);
            theCache.put(beanClass, beanInfo);
        }
        return beanInfo;
    }
}
