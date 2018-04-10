/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.context;

import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;

import java.lang.reflect.Method;

/**
 * <p>Calls a method that constructs the object</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MethodConstructorInjectionPoint extends DefaultMethodInjectionPoint implements ConstructorInjectionPoint {

    public MethodConstructorInjectionPoint(BeanDefinition declaringComponent, Method method, boolean requiresReflection, Argument... arguments) {
        super(declaringComponent, method, requiresReflection, arguments);
    }

    @Override
    public Object invoke(Object... args) {
        throw new UnsupportedOperationException("Use MethodInjectionPoint#invoke(..) instead");
    }
}
