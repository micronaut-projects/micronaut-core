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

package io.micronaut.configuration.hibernate.validator;

import io.micronaut.context.BeanContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;
import javax.validation.ParameterNameProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link ParameterNameProvider} interface that.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultParameterNameProvider implements ParameterNameProvider {

    private static final Set<String> INTERNAL_CLASS_NAMES = CollectionUtils.setOf(Object.class.getName(), "groovy.lang.GroovyObject");
    private final BeanContext beanContext;

    /**
     * Constructor.
     *
     * @param beanContext beanContext
     */
    public DefaultParameterNameProvider(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<String> getParameterNames(Constructor<?> constructor) {
        Class<?> declaringClass = constructor.getDeclaringClass();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (INTERNAL_CLASS_NAMES.contains(declaringClass.getName())) {
            return doGetParameterNames(constructor);
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
            return doGetParameterNames(method);
        }

        Optional<? extends ExecutableMethod<?, Object>> executableMethod = beanContext.findExecutableMethod(declaringClass, method.getName(), parameterTypes);
        return executableMethod.map(m -> Arrays.asList(m.getArgumentNames())).orElse(defaultParameterTypes(parameterTypes));
    }

    /**
     * Add the parameter types to a list of names.
     *
     * @param parameterTypes parameterTypes
     * @return list of strings
     */
    protected List<String> defaultParameterTypes(Class<?>[] parameterTypes) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < parameterTypes.length; i++) {
            names.add("arg" + i);
        }
        return names;
    }

    private List<String> doGetParameterNames(Executable executable) {
        Parameter[] parameters = executable.getParameters();
        List<String> parameterNames = new ArrayList<>(parameters.length);

        for (Parameter parameter : parameters) {
            parameterNames.add(parameter.getName());
        }

        return Collections.unmodifiableList(parameterNames);
    }
}
