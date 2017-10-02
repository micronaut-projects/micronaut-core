/*
 * Copyright 2017 original authors
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
package org.particleframework.core.type;

import org.particleframework.core.annotation.Internal;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link ArgumentValue}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultArgumentValue<V> implements ArgumentValue<V> {
    private final Argument<V> argument;
    private final V value;

    DefaultArgumentValue(Argument<V> argument, V value) {
        this.argument = argument;
        this.value = value;
    }

    @Override
    public String getName() {
        return argument.getName();
    }

    @Override
    public Class<V> getType() {
        return argument.getType();
    }

    @Override
    public Optional<Argument<?>> getFirstTypeVariable() {
        return argument.getFirstTypeVariable();
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return argument.getTypeVariables();
    }

    @Override
    public Annotation getQualifier() {
        return argument.getQualifier();
    }

    @Override
    public <A extends Annotation> A findAnnotation(Class<A> stereotype) {
        return argument.findAnnotation(stereotype);
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return argument.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return argument.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }
}
