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
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Composes ordinary Java definitions with lightweight references to deferred Python definitions. */
@Internal
public final class RuntimePythonBeanDefinitionsProvider implements BeanDefinitionsProvider {
    /** Creates a provider that composes ordinary and runtime-generated definitions. */
    public RuntimePythonBeanDefinitionsProvider() {
    }

    @Override
    public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
        List<BeanDefinitionReference<?>> references = new ArrayList<>(new DefaultBeanDefinitionsProvider().provide(classLoader));
        Set<String> names = new LinkedHashSet<>();
        try {
            var indexes = classLoader.getResources(RuntimePythonModel.PATH + "index");
            while (indexes.hasMoreElements()) {
                try (var reader = new BufferedReader(new InputStreamReader(indexes.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    reader.lines().map(String::trim).filter(name -> !name.isEmpty()).forEach(names::add);
                }
            }
            for (String name : names) {
                Class<?> beanType = Class.forName(name, false, classLoader);
                if (RuntimePythonModel.read(beanType).singleton()) {
                    references.add(new Reference(beanType));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Cannot discover Python runtime metadata", e);
        }
        return references;
    }

    private static final class Reference implements BeanDefinitionReference<Object> {
        private final Class<Object> beanType;
        private final AnnotationMetadata metadata;

        @SuppressWarnings("unchecked")
        private Reference(Class<?> beanType) {
            this.beanType = (Class<Object>) beanType;
            metadata = RuntimePythonModel.metadata(true);
        }

        @Override
        public Class<Object> getBeanType() {
            return beanType;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return metadata;
        }

        @Override
        public String getBeanDefinitionName() {
            return beanType.getPackageName() + ".$" + beanType.getSimpleName() + "$RuntimeDefinition";
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public BeanDefinition<Object> load() {
            return (BeanDefinition<Object>) RuntimePythonMetadata.definition(beanType);
        }
    }
}
