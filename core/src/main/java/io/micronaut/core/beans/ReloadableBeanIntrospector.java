/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Internal;

/**
 * A {@link BeanIntrospector} that indexes the introspections of each class loader, and can forget the index of a
 * loader whose classes changed. Only a development launcher, which replaces the application's classes, needs it.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public interface ReloadableBeanIntrospector extends BeanIntrospector {

    /**
     * Forgets the indexed introspections of the given class loader, and of every loader that delegates to it, so that
     * the next lookup through them scans the classpath again. The cached service entries of every loader are
     * forgotten too.
     *
     * @param changed The class loader whose visible classes changed, such as a development launcher's delegating
     * loader after it swapped the generation it delegates to, or a retired generation
     */
    void invalidate(ClassLoader changed);

    /**
     * Forgets what the {@link BeanIntrospector#SHARED shared introspector} indexed for the given loader, if it keeps an
     * index.
     *
     * @param changed The class loader whose visible classes changed
     * @see #invalidate(ClassLoader)
     */
    static void invalidateShared(ClassLoader changed) {
        if (BeanIntrospector.SHARED instanceof ReloadableBeanIntrospector reloadable) {
            reloadable.invalidate(changed);
        }
    }
}
