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
import io.micronaut.core.io.service.SoftServiceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * The default implementation of {@link BeanIntrospectionsProvider}.
 *
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
final class DefaultBeanIntrospectionsProvider implements BeanIntrospectionsProvider {

    @Override
    @SuppressWarnings("java:S3740")
    public List<BeanIntrospectionReference<Object>> provide(ClassLoader classLoader) {
        final SoftServiceLoader<BeanIntrospectionReference> services =
            SoftServiceLoader.load(BeanIntrospectionReference.class, classLoader);
        List<BeanIntrospectionReference<Object>> beanIntrospectionReferences = new ArrayList<>(300);
        services.collectAll(
            (List) beanIntrospectionReferences,
            BeanIntrospectionReference::isPresent
        );
        return beanIntrospectionReferences;
    }
}
