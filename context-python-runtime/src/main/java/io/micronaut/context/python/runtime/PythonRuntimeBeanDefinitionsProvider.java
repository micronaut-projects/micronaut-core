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

import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Composes the references of another provider with the runtime references of the catalogs of the class loader.
 * A class that has both a build-time definition and a runtime model is an error: the two backends must not both own
 * it.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeBeanDefinitionsProvider implements BeanDefinitionsProvider {

    private final BeanDefinitionsProvider delegate;

    /**
     * Composes with the default provider.
     */
    public PythonRuntimeBeanDefinitionsProvider() {
        this(new DefaultBeanDefinitionsProvider());
    }

    /**
     * @param delegate The provider whose references are kept
     */
    public PythonRuntimeBeanDefinitionsProvider(BeanDefinitionsProvider delegate) {
        this.delegate = delegate;
    }

    /**
     * @return The composed provider
     */
    public BeanDefinitionsProvider getDelegate() {
        return delegate;
    }

    @Override
    public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
        List<BeanDefinitionReference<?>> references = new ArrayList<>(delegate.provide(classLoader));
        List<PythonMetadataCatalog.Entry> entries = PythonMetadataCatalog.of(classLoader).entries();
        if (entries.isEmpty()) {
            return references;
        }
        Set<String> names = new HashSet<>();
        for (BeanDefinitionReference<?> reference : references) {
            names.add(reference.getBeanDefinitionName());
        }
        for (PythonMetadataCatalog.Entry entry : entries) {
            if (!entry.bean()) {
                continue;
            }
            Class<?> beanType;
            try {
                beanType = Class.forName(entry.className(), false, classLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("The Python runtime catalog " + entry.source() + " lists " + entry.className()
                    + " but the class is not on the class path", e);
            }
            BeanDefinitionModel definition = PythonRuntimeMetadata.model(beanType).classModel().beanDefinition();
            if (definition == null) {
                throw new IllegalStateException("The Python runtime catalog " + entry.source() + " lists a bean definition for "
                    + entry.className() + " but its model has none");
            }
            if (!names.add(definition.definitionClassName())) {
                throw new IllegalStateException("Both a build-time bean definition and a runtime model exist for " + entry.className()
                    + " (" + definition.definitionClassName() + "); recompile the Python sources with one metadata backend");
            }
            references.add(new PythonRuntimeBeanReference(beanType, definition));
        }
        return references;
    }
}
