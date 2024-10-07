/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.beans;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.type.Argument;

/**
 * A variation of {@link AbstractInitializableBeanIntrospection} that is also a {@link io.micronaut.core.beans.BeanIntrospectionReference}.
 *
 * @param <B> The bean type
 * @author Denis Stepanov
 * @since 4.4.0
 */
public abstract class AbstractInitializableBeanIntrospectionAndReference<B> extends AbstractInitializableBeanIntrospection<B> implements BeanIntrospectionReference<B> {

    protected AbstractInitializableBeanIntrospectionAndReference(Class<B> beanType,
                                                                 AnnotationMetadata annotationMetadata,
                                                                 AnnotationMetadata constructorAnnotationMetadata,
                                                                 Argument<?>[] constructorArguments,
                                                                 BeanPropertyRef<Object>[] propertiesRefs,
                                                                 BeanMethodRef<Object>[] methodsRefs) {
        super(beanType, annotationMetadata, constructorAnnotationMetadata, constructorArguments, propertiesRefs, methodsRefs);
    }

    @Override
    public BeanIntrospection<B> load() {
        return this;
    }

    @Override
    public String getName() {
        return getBeanType().getName();
    }

    @Override
    public final boolean isPresent() {
        return true;
    }

}
