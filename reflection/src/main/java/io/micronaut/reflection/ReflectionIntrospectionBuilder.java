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
package io.micronaut.reflection;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A builder over the builder type {@link Introspected#builder()} configures: an instance of it is written
 * through its write methods and asked to build the bean, as a generated introspection with a builder does.
 *
 * @param <T> The bean type
 * @since 5.2.0
 */
@Internal
final class ReflectionIntrospectionBuilder<T> implements BeanIntrospection.Builder<T> {

    /**
     * The builder {@link Introspected#builder()} configures, resolved the way the processor resolves it: the
     * builder type - the one {@code builderClass} names, or the return type of the static {@code builderMethod}
     * of the type - the way to an instance of it, its write methods and the method building the bean.
     *
     * @param <T> The bean type
     */
    static final class Support<T> {

        private final Class<T> beanType;
        private final Class<?> builderType;
        private final @Nullable Method builderFactory;
        private final @Nullable Constructor<?> builderConstructor;
        private final Method creator;
        private final Method[] writers;
        private final Argument<?>[] arguments;
        private final Argument<?>[] creatorArguments;

        private Support(Class<T> beanType,
                               Class<?> builderType,
                               @Nullable Method builderFactory,
                               @Nullable Constructor<?> builderConstructor,
                               Method creator,
                               Method[] writers,
                               Argument<?>[] arguments) {
            this.beanType = beanType;
            this.builderType = builderType;
            this.builderFactory = builderFactory;
            this.builderConstructor = builderConstructor;
            this.creator = creator;
            this.writers = writers;
            this.arguments = arguments;
            this.creatorArguments = ReflectionArguments.argumentsOf(creator, builderType);
        }

        /**
         * The builder the type configures, {@code null} when {@link Introspected#builder()} names neither a
         * builder class nor a builder method.
         */
        static <T> @Nullable Support<T> of(Class<T> beanType, AnnotationMetadata metadata) {
            AnnotationValue<Introspected.IntrospectionBuilder> builder = metadata.findAnnotation(Introspected.class)
                .flatMap(introspected -> introspected.getAnnotation("builder", Introspected.IntrospectionBuilder.class))
                .orElse(null);
            if (builder == null) {
                return null;
            }
            String builderMethod = builder.stringValue("builderMethod").filter(name -> !name.isEmpty()).orElse(null);
            Class<?> builderClass = builder.classValue("builderClass").filter(type -> type != void.class).orElse(null);
            if (builderMethod == null && builderClass == null) {
                return null;
            }
            String creatorMethod = builder.stringValue("creatorMethod").filter(name -> !name.isEmpty()).orElse("build");
            String[] writePrefixes = builder.getAnnotation("accessorStyle", AccessorsStyle.class)
                .map(style -> style.stringValues("writePrefixes"))
                .filter(prefixes -> prefixes.length > 0)
                .orElse(new String[] {""});

            Method builderFactory = null;
            Constructor<?> builderConstructor = null;
            Class<?> builderType;
            if (builderMethod != null) {
                builderFactory = staticMethod(beanType, candidate -> candidate.getName().equals(builderMethod)
                    && candidate.getParameterCount() == 0 && candidate.getReturnType() != void.class);
                if (builderFactory == null) {
                    throw new IntrospectionException("Method " + builderMethod + "() specified by builderMethod not found on " + beanType.getName() + ". The method must be static and accessible.");
                }
                builderType = builderFactory.getReturnType();
            } else {
                builderType = Objects.requireNonNull(builderClass);
                for (Constructor<?> candidate : builderType.getDeclaredConstructors()) {
                    if (candidate.getParameterCount() == 0 && !Modifier.isPrivate(candidate.getModifiers())) {
                        builderConstructor = candidate;
                        break;
                    }
                }
                if (builderConstructor == null) {
                    // no constructor to call: a static method of the type returning the builder
                    builderFactory = staticMethod(beanType, candidate -> candidate.getParameterCount() == 0
                        && builderType.isAssignableFrom(candidate.getReturnType()));
                    if (builderFactory == null) {
                        throw new IntrospectionException("No accessible constructor or builder() method found for builder: " + builderType.getName());
                    }
                }
            }
            Method creator = null;
            List<Method> writers = new ArrayList<>();
            for (Method candidate : builderType.getMethods()) {
                if (Modifier.isStatic(candidate.getModifiers()) || candidate.isSynthetic() || candidate.isBridge()
                    || candidate.getDeclaringClass() == Object.class || ReflectionBeanIntrospection.isGroovyObjectMethod(candidate)) {
                    continue;
                }
                if (creator == null && candidate.getName().equals(creatorMethod) && beanType.isAssignableFrom(candidate.getReturnType())) {
                    creator = candidate;
                } else if (candidate.getParameterCount() <= 1
                    && candidate.getReturnType().isAssignableFrom(builderType)
                    && Arrays.stream(writePrefixes).anyMatch(candidate.getName()::startsWith)) {
                    writers.add(candidate);
                }
            }
            if (creator == null) {
                throw new IntrospectionException("No build method found in builder: " + builderType.getName());
            }
            // reflection reports the methods in no order: by name, then by parameter type, the same on every JVM
            writers.sort(Comparator.comparing(Method::getName).thenComparing(candidate -> Arrays.toString(candidate.getParameterTypes())));
            List<Method> selected = new ArrayList<>(writers.size());
            List<Argument<?>> arguments = new ArrayList<>(writers.size());
            Set<String> names = new HashSet<>();
            for (Method writer : writers) {
                Argument<?> argument = writer.getParameterCount() == 0
                    ? Argument.of(Boolean.class, writer.getName())
                    : argumentOf(writer.getParameters()[0], writer, builderType, writePrefixes);
                // the first method of a name is the one written through, as a generated builder keeps it
                if (names.add(argument.getName())) {
                    selected.add(writer);
                    arguments.add(argument);
                }
            }
            for (Method writer : selected) {
                writer.trySetAccessible();
            }
            creator.trySetAccessible();
            if (builderConstructor != null) {
                builderConstructor.trySetAccessible();
            }
            if (builderFactory != null) {
                builderFactory.trySetAccessible();
            }
            return new Support<>(beanType, builderType, builderFactory, builderConstructor, creator,
                selected.toArray(Method[]::new), arguments.toArray(Argument[]::new));
        }

        /**
         * The argument a write method takes, boxed as a generated builder boxes it, named after the parameter
         * when the class file carries its name and after the property the method writes otherwise.
         */
        private static Argument<?> argumentOf(Parameter parameter, Method writer, Class<?> builderType, String[] writePrefixes) {
            Argument<?> argument = ReflectionArguments.of(parameter, builderType);
            String name = parameter.isNamePresent() ? parameter.getName() : propertyOf(writer.getName(), writePrefixes);
            Class<?> type = argument.getType().isPrimitive() ? ReflectionUtils.getWrapperType(argument.getType()) : argument.getType();
            return Argument.of(type, name, argument.getAnnotationMetadata(), argument.getTypeParameters());
        }

        private static String propertyOf(String method, String[] writePrefixes) {
            for (String prefix : writePrefixes) {
                if (!prefix.isEmpty() && method.startsWith(prefix) && method.length() > prefix.length()) {
                    return NameUtils.decapitalize(method.substring(prefix.length()));
                }
            }
            return method;
        }

        private static @Nullable Method staticMethod(Class<?> type, Predicate<Method> filter) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (Modifier.isStatic(candidate.getModifiers()) && !Modifier.isPrivate(candidate.getModifiers())
                    && !candidate.isSynthetic() && filter.test(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        Object newBuilder() throws ReflectiveOperationException {
            if (builderFactory != null) {
                return builderFactory.invoke(null);
            }
            return Objects.requireNonNull(builderConstructor).newInstance();
        }
    }


    private final ReflectionBeanIntrospection<T> introspection;
    private final Support<T> support;
    private final Object[] values;

    ReflectionIntrospectionBuilder(ReflectionBeanIntrospection<T> introspection, Support<T> support) {
        this.introspection = introspection;
        this.support = support;
        this.values = new Object[support.arguments.length];
    }

    @Override
    public Argument<?>[] getBuilderArguments() {
        return support.arguments;
    }

    @Override
    public Argument<?>[] getBuildMethodArguments() {
        return support.creatorArguments;
    }

    @Override
    public int indexOf(String name) {
        for (int i = 0; i < support.arguments.length; i++) {
            if (support.arguments[i].getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BeanIntrospection.Builder<T> with(String name, @Nullable Object value) {
        int index = indexOf(name);
        if (index != -1) {
            with(index, (Argument<Object>) support.arguments[index], value);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BeanIntrospection.Builder<T> with(T existing) {
        for (BeanProperty<T, Object> property : introspection.getBeanProperties()) {
            int index = indexOf(property.getName());
            if (index != -1 && !property.isWriteOnly() && copyable(property)) {
                with(index, (Argument<Object>) support.arguments[index], property.get(existing));
            }
        }
        return this;
    }

    /**
     * Whether a generated builder copies the property from an existing instance: when it has a setter, or
     * when it is a parameter of a constructor the processors select - an accessible one. A property answered
     * by a getter over a final field, of a type built through its builder alone, is not copied.
     */
    private boolean copyable(BeanProperty<T, Object> property) {
        if (!property.isReadOnly()) {
            return true;
        }
        Constructor<T> constructor = introspection.selectedConstructor();
        if (constructor == null || Modifier.isPrivate(constructor.getModifiers())) {
            return false;
        }
        for (Argument<?> argument : introspection.getConstructorArguments()) {
            if (argument.getName().equals(property.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <A> BeanIntrospection.Builder<T> with(int index, Argument<A> argument, @Nullable A value) {
        if (value != null) {
            if (!argument.isInstance(value)) {
                throw new IllegalArgumentException("Invalid value [" + value + "] specified for argument [" + argument + "]");
            }
            values[index] = value;
        }
        return this;
    }

    @Override
    public <A> BeanIntrospection.Builder<T> convert(int index, ArgumentConversionContext<A> argument, @Nullable Object value, ConversionService conversionService) {
        if (value != null) {
            values[index] = argument.getArgument().isInstance(value) ? value : conversionService.convertRequired(value, argument);
        }
        return this;
    }

    @Override
    public T build() {
        return build(new Object[0]);
    }

    @Override
    public T build(Object... buildMethodArguments) {
        Class<T> beanType = support.beanType;
        try {
            Object builder = support.newBuilder();
            for (int i = 0; i < values.length; i++) {
                Method writer = support.writers[i];
                Object value = values[i];
                Object result;
                if (writer.getParameterCount() == 0) {
                    // a flag: the method is called when the flag is set
                    if (!Boolean.TRUE.equals(value)) {
                        continue;
                    }
                    result = writer.invoke(builder);
                } else if (value != null) {
                    result = writer.invoke(builder, value);
                } else if (support.arguments[i].isDeclaredNullable()) {
                    result = writer.invoke(builder, new Object[] {null});
                } else {
                    continue;
                }
                // a fluent builder answers itself, or another builder to go on with
                if (result != null && support.builderType.isInstance(result)) {
                    builder = result;
                }
            }
            return beanType.cast(support.creator.invoke(builder, buildMethodArguments));
        } catch (InvocationTargetException e) {
            throw new InstantiationException("Cannot build " + beanType.getName() + ": " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new InstantiationException("Cannot build " + beanType.getName() + ": " + e.getMessage(), e);
        }
    }
}
