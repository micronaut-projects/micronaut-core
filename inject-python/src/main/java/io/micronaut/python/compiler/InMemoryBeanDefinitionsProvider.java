/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.compiler;

import io.micronaut.aop.internal.InterceptorRegistryBean;
import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.context.event.ApplicationEventPublisherFactory;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.provider.BeanProviderDefinition;
import io.micronaut.inject.provider.JakartaProviderBeanDefinition;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * An in-memory version of {@link BeanDefinitionsProvider} for use with a class loader produced by {@link PyronautCompiler#buildClassLoader()}.
 */
public class InMemoryBeanDefinitionsProvider implements BeanDefinitionsProvider {
    private final boolean includeAllBeans;

    public InMemoryBeanDefinitionsProvider(boolean includeAllBeans) {
        this.includeAllBeans = includeAllBeans;
    }

    public InMemoryBeanDefinitionsProvider() {
        this(true);
    }

    @Override
    public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
        List<BeanDefinitionReference<?>> references = new ArrayList<>();

        // Add references for any generated bean definitions from Python processing
        try {
            // Look for generated bean definition references in the Python classloader
            Enumeration<URL> resources = classLoader.getResources("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String className = resource.toString().substring("mem:/CLASS_OUTPUT/META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/".length());
                BeanDefinitionReference beanDefRef = (BeanDefinitionReference) InstantiationUtils.tryInstantiate(className, classLoader).orElse(null);
                if (beanDefRef != null) {
                    references.add(beanDefRef);
                }
            }

        } catch (Exception e) {
            // No bean definitions found, continue
        }

        var allReferences = new DefaultBeanDefinitionsProvider().provide(InMemoryBeanDefinitionsProvider.class.getClassLoader());
        List<BeanDefinitionReference<?>> builtInBeanReferences = getBuiltInBeanReferences();
        List<BeanDefinitionReference<?>> finalList;
        if (includeAllBeans) {
            finalList = new ArrayList<>(references.size() + allReferences.size());
            finalList.addAll(references);
            finalList.addAll(allReferences);
        } else {
            finalList = new ArrayList<>(references.size() + builtInBeanReferences.size());
            var pythonOnly = allReferences.stream().filter(ref -> {
                var className = ref.getClass().getName();
                return className.startsWith("io.micronaut.context.python") || className.startsWith(AbstractPythonClassElement.PYTHON_DEFAULT_PACKAGE);
            }).toList();

            finalList.addAll(references);
            finalList.addAll(pythonOnly);
            finalList.addAll(builtInBeanReferences);
        }
        return finalList;
    }

    protected List<BeanDefinitionReference<?>> getBuiltInBeanReferences() {
        return List.of(
                new InterceptorRegistryBean(),
                new BeanProviderDefinition(),
                new JakartaProviderBeanDefinition(),
                new ApplicationEventPublisherFactory<>()
        );
    }
}
