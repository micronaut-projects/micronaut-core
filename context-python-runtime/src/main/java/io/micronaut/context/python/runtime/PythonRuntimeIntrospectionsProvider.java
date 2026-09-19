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
package io.micronaut.context.python.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospectionProviders;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.beans.BeanIntrospectionsProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Composes the introspection references of another provider with the runtime references of the catalogs of the
 * class loader, so that enumeration and filtering see runtime-generated introspections without generating them.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeIntrospectionsProvider implements BeanIntrospectionsProvider {

    private final BeanIntrospectionsProvider delegate;

    /**
     * @param delegate The provider whose references are kept
     */
    public PythonRuntimeIntrospectionsProvider(BeanIntrospectionsProvider delegate) {
        this.delegate = delegate;
    }

    /**
     * Installs the composite over the current global provider, once.
     */
    public static synchronized void install() {
        BeanIntrospectionsProvider current = BeanIntrospectionProviders.get();
        if (!(current instanceof PythonRuntimeIntrospectionsProvider)) {
            BeanIntrospectionProviders.set(new PythonRuntimeIntrospectionsProvider(current));
        }
    }

    @Override
    public List<BeanIntrospectionReference<Object>> provide(ClassLoader classLoader) {
        List<BeanIntrospectionReference<Object>> references = new ArrayList<>(delegate.provide(classLoader));
        List<PythonMetadataCatalog.Entry> entries = PythonMetadataCatalog.of(classLoader).entries();
        if (entries.isEmpty()) {
            return references;
        }
        Set<String> names = new HashSet<>();
        for (BeanIntrospectionReference<Object> reference : references) {
            names.add(reference.getName());
        }
        for (PythonMetadataCatalog.Entry entry : entries) {
            if (!entry.introspection()) {
                continue;
            }
            Class<?> beanType;
            try {
                beanType = Class.forName(entry.className(), false, classLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("The Python runtime catalog " + entry.source() + " lists " + entry.className()
                    + " but the class is not on the class path", e);
            }
            if (!names.add(entry.className())) {
                throw new IllegalStateException("Both a build-time introspection and a runtime model exist for " + entry.className()
                    + "; recompile the Python sources with one metadata backend");
            }
            references.add(new PythonRuntimeIntrospectionReference(beanType));
        }
        return references;
    }
}
