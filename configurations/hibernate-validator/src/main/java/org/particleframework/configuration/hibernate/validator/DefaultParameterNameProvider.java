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
package org.particleframework.configuration.hibernate.validator;

import org.particleframework.context.BeanContext;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;

import javax.inject.Singleton;
import javax.validation.ParameterNameProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link ParameterNameProvider} interface that
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultParameterNameProvider implements ParameterNameProvider {
    private static final Set<String> INTERNAL_CLASS_NAMES = CollectionUtils.setOf(Object.class.getName(), "groovy.lang.GroovyObject");

    private final BeanContext beanContext;

    public DefaultParameterNameProvider(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<String> getParameterNames(Constructor<?> constructor) {
        Class<?> declaringClass = constructor.getDeclaringClass();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (INTERNAL_CLASS_NAMES.contains(declaringClass.getName())) {
            return defaultParameterTypes(parameterTypes);
        }
        Optional<? extends BeanDefinition<?>> definition = beanContext.findBeanDefinition(declaringClass);
        return definition.map(def ->
                Arrays.stream(def.getConstructor().getArguments()).map(Argument::getName).collect(Collectors.toList())
        ).orElse(defaultParameterTypes(parameterTypes));
    }

    @Override
    public List<String> getParameterNames(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Class<?> declaringClass = method.getDeclaringClass();
        if (INTERNAL_CLASS_NAMES.contains(declaringClass.getName())) {
            return defaultParameterTypes(parameterTypes);
        }

        Optional<? extends ExecutableMethod<?, Object>> executableMethod = beanContext.findExecutableMethod(declaringClass, method.getName(), parameterTypes);
        return executableMethod.map(m -> Arrays.asList(m.getArgumentNames())).orElse(defaultParameterTypes(parameterTypes));
    }

    protected List<String> defaultParameterTypes(Class<?>[] parameterTypes) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < parameterTypes.length; i++) {
            names.add("arg" + i);
        }
        return names;
    }
}
