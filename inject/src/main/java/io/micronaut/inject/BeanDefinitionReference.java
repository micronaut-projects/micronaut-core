/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;

import javax.inject.Singleton;

/**
 * <p>A bean definition reference provides a reference to a {@link BeanDefinition} thus
 * allowing for soft loading of bean definitions without loading the actual types.</p>
 *
 * <p>This interface implements {@link io.micronaut.core.annotation.AnnotationMetadataProvider} thus allowing the bean
 * metadata to be introspected safely without loading the class or the annotations themselves.</p>
 *
 * <p>The actual bean will be loaded upon calling the {@link #load()} method. Note that consumers of this interface
 * should call {@link #isPresent()} prior to loading to ensure an error does not occur</p>
 *
 * <p>The class can also decide whether to abort loading the definition by returning null</p>
 *
 * <p>This interface extends the {@link BeanType} interface which is shared between {@link BeanDefinition} and this type. In addition a
 * reference can be enabled or disabled (see {@link BeanContextConditional#isEnabled(BeanContext)})</p>
 *
 * @see BeanType
 * @see BeanDefinition
 * @see BeanContextConditional
 * @param <T> The bean type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface BeanDefinitionReference<T> extends BeanType<T> {

    /**
     * @return The class name of the backing {@link BeanDefinition}
     */
    String getBeanDefinitionName();

    /**
     * Loads the bean definition.
     *
     * @return The loaded component definition or null if it shouldn't be loaded
     */
    BeanDefinition<T> load();

    /**
     * Loads the bean definition for the current {@link BeanContext}.
     *
     * @param context The bean context
     * @return The loaded bean definition or null if it shouldn't be loaded
     */
    BeanDefinition<T> load(BeanContext context);

    /**
     * @return Is this class context scope
     */
    boolean isContextScope();

    /**
     * @return Is the underlying bean type present on the classpath
     */
    boolean isPresent();

    /**
     * @return Is this bean a singleton.
     * @since 2.0
     */
    default boolean isSingleton() {
        AnnotationMetadata am = getAnnotationMetadata();
        return am.hasDeclaredStereotype(Singleton.class) ||
               am.classValue(DefaultScope.class).map(t -> t == Singleton.class).orElse(false);
    }

    /**
     * @return Is this bean a configuration properties.
     * @since 2.0
     */
    default  boolean isConfigurationProperties() {
        return getAnnotationMetadata().hasDeclaredStereotype(ConfigurationReader.class);
    }
}
