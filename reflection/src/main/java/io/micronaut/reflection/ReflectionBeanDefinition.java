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

import io.micronaut.context.AbstractInitializableBeanDefinition;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A {@link BeanDefinition} over a {@link Class}, with the injection points, the executable methods and the life
 * cycle a generated definition would have, to register a type the annotation processors never saw with a
 * {@link io.micronaut.context.BeanDefinitionRegistry#registerBeanDefinition(RuntimeBeanDefinition) bean context}
 * at runtime.
 *
 * <p>The definition is read from the class as the processors read it at compilation time:</p>
 * <ul>
 *     <li>the constructor is the one annotated {@code @Inject} or {@link Creator}, else the only accessible one,
 *     else the accessible one with no parameter;</li>
 *     <li>the non-static, non-final fields annotated {@code @Inject}, {@link Value} or {@link Property} are
 *     injected, the super classes' first;</li>
 *     <li>the methods annotated {@code @Inject} and the setters annotated with a qualifier - {@link Value} is
 *     one - are injected, the methods annotated {@code @PostConstruct} and {@code @PreDestroy} are the life cycle
 *     methods;</li>
 *     <li>the methods annotated {@link Executable}, and the public methods of a type annotated {@link Executable},
 *     are the executable methods;</li>
 *     <li>the scope, the qualifier, the order and the conditions come from the annotations of the class, unless the
 *     {@link Builder builder} overrides them.</li>
 * </ul>
 *
 * <p>Each injected argument is resolved as a generated definition resolves it: a bean by type and qualifier, a
 * {@link Collection}, an array, a {@link Stream}, an {@link Optional} or a {@link Map} of beans, a
 * {@link BeanRegistration} or a collection of them, a configuration {@link Value} or {@link Property}. The
 * features that need the processors - {@code @ConfigurationProperties} binding, {@code @EachProperty} and
 * {@code @EachBean} iteration, {@code @Parameter} arguments, AOP advice - are not available to a reflective
 * definition.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
@SuppressWarnings("ArrayRecordComponent")
public final class ReflectionBeanDefinition<T> extends AbstractInitializableBeanDefinition<T>
    implements RuntimeBeanDefinition<T>, InitializingBeanDefinition<T>, DisposableBeanDefinition<T> {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final String beanDefinitionName;
    private final Constructor<T> constructor;
    private final Argument<?>[] constructorArguments;
    private final Field[] fields;
    private final Argument<?>[] fieldArguments;
    private final Method[] methods;
    private final Argument<?>[][] methodArguments;
    private final int[] injectMethods;
    private final int[] postConstructMethods;
    private final int[] preDestroyMethods;
    @Nullable
    private final Qualifier<T> qualifier;
    private final Set<Class<?>> exposedTypes;
    private final int order;

    private ReflectionBeanDefinition(Class<T> type,
                                     AnnotationMetadata annotationMetadata,
                                     Members<T> members,
                                     Map<String, Argument<?>[]> typeArguments,
                                     PrecalculatedInfo precalculatedInfo,
                                     @Nullable Qualifier<T> qualifier,
                                     Set<Class<?>> exposedTypes) {
        super(type,
            members.constructorReference,
            annotationMetadata,
            members.methodReferences.length == 0 ? null : members.methodReferences,
            members.fieldReferences.length == 0 ? null : members.fieldReferences,
            null,
            members.executableMethods,
            typeArguments.isEmpty() ? null : typeArguments,
            precalculatedInfo);
        this.beanDefinitionName = type.getName() + "$ReflectionDefinition" + COUNTER.incrementAndGet();
        this.constructor = members.constructor;
        this.constructorArguments = members.constructorReference.arguments;
        this.fields = members.fields.toArray(Field[]::new);
        this.fieldArguments = new Argument[members.fieldReferences.length];
        for (int i = 0; i < fieldArguments.length; i++) {
            fieldArguments[i] = members.fieldReferences[i].argument;
        }
        this.methods = members.methods.toArray(Method[]::new);
        this.methodArguments = new Argument[members.methodReferences.length][];
        List<Integer> inject = new ArrayList<>();
        List<Integer> postConstruct = new ArrayList<>();
        List<Integer> preDestroy = new ArrayList<>();
        for (int i = 0; i < members.methodReferences.length; i++) {
            MethodReference reference = members.methodReferences[i];
            methodArguments[i] = reference.arguments;
            if (reference.isPostConstructMethod) {
                postConstruct.add(i);
            } else if (reference.isPreDestroyMethod) {
                preDestroy.add(i);
            } else {
                inject.add(i);
            }
        }
        this.injectMethods = inject.stream().mapToInt(Integer::intValue).toArray();
        this.postConstructMethods = postConstruct.stream().mapToInt(Integer::intValue).toArray();
        this.preDestroyMethods = preDestroy.stream().mapToInt(Integer::intValue).toArray();
        this.qualifier = qualifier;
        this.exposedTypes = exposedTypes;
        this.order = OrderUtil.getOrder(annotationMetadata);
    }

    /**
     * The definition of a type, read from its annotations.
     *
     * @param type The type
     * @param <T>  The bean type
     * @return The definition
     * @throws IllegalArgumentException When the type cannot be a bean: an interface, an abstract class, a
     *                                  non-static inner class or a type without an accessible constructor
     */
    public static <T> ReflectionBeanDefinition<T> of(Class<T> type) {
        return builder(type).build();
    }

    /**
     * A builder of the definition of a type, to override what its annotations declare.
     *
     * @param type The type
     * @param <T>  The bean type
     * @return The builder
     */
    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    /**
     * The constructor the bean is instantiated with.
     *
     * @return The constructor
     */
    public Constructor<T> getTargetConstructor() {
        return constructor;
    }

    @Override
    public String getBeanDefinitionName() {
        return beanDefinitionName;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    @Nullable
    public Qualifier<T> getDeclaredQualifier() {
        return qualifier != null ? qualifier : super.getDeclaredQualifier();
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return exposedTypes;
    }

    @Override
    public List<Argument<?>> getTypeArguments(@Nullable Class<?> type) {
        return type == null ? Collections.emptyList() : getTypeArguments(type.getName());
    }

    /**
     * Configures the definition for the context it is loaded by, as the reference of a generated definition
     * does: the property expressions of the metadata resolve against the environment of an application context.
     */
    @Override
    public BeanDefinition<T> load(BeanContext context) {
        if (context instanceof ApplicationContext applicationContext) {
            configure(applicationContext.getEnvironment());
        }
        configure(context);
        return this;
    }

    @Override
    public T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        Object[] arguments = new Object[constructorArguments.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = resolveConstructorArgument(resolutionContext, context, i);
        }
        T bean;
        try {
            bean = constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new BeanInstantiationException(resolutionContext, e.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new BeanInstantiationException(resolutionContext, e);
        }
        bean = inject(resolutionContext, context, bean);
        return initialize(resolutionContext, context, bean);
    }

    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        for (int i = 0; i < fields.length; i++) {
            Object value = resolveFieldValue(resolutionContext, context, i);
            Field field = fields[i];
            if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                ClassUtils.REFLECTION_LOGGER.debug("Bean of type [{}] uses reflection to inject field: '{}'", getBeanType(), field.getName());
            }
            try {
                field.set(bean, value);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new DependencyInjectionException(resolutionContext, "Error setting field value: " + e.getMessage(), e);
            }
        }
        for (int index : injectMethods) {
            invokeMethod(resolutionContext, context, index, bean);
        }
        return bean;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        T initialized = (T) postConstruct(resolutionContext, context, bean);
        for (int index : postConstructMethods) {
            invokeMethod(resolutionContext, context, index, initialized);
        }
        return initialized;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        T disposed = (T) preDestroy(resolutionContext, context, bean);
        for (int index : preDestroyMethods) {
            invokeMethod(resolutionContext, context, index, disposed);
        }
        return disposed;
    }

    @Override
    public String toString() {
        return "ReflectionBeanDefinition(" + getBeanType().getName() + ")";
    }

    private void invokeMethod(BeanResolutionContext resolutionContext, BeanContext context, int index, T bean) {
        Argument<?>[] arguments = methodArguments[index];
        Object[] values = new Object[arguments.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = resolveMethodArgument(resolutionContext, context, index, i);
        }
        Method method = methods[index];
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Bean of type [{}] uses reflection to inject method: '{}'", getBeanType(), method.getName());
        }
        try {
            method.invoke(bean, values);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof BeanContextException exception) {
                throw exception;
            }
            throw new DependencyInjectionException(resolutionContext, "Error invoking method: " + method.getName(), e.getTargetException());
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new DependencyInjectionException(resolutionContext, "Error invoking method: " + method.getName(), e);
        }
    }

    @Nullable
    @SuppressWarnings({"deprecation", "unchecked", "rawtypes", "NullAway"}) // the base class accepts a null qualifier and resolves it from the argument
    private Object resolveConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int index) {
        Argument<?> argument = constructorArguments[index];
        return switch (Injection.of(argument)) {
            case BEAN -> getBeanForConstructorArgument(resolutionContext, context, index, qualifierOf(argument));
            case BEANS -> toCollectionType(argument, getBeansOfTypeForConstructorArgument(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument)));
            case REGISTRATIONS -> toCollectionType(argument, getBeanRegistrationsForConstructorArgument(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument)));
            case REGISTRATION -> getBeanRegistrationForConstructorArgument(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case MAP -> getMapOfTypeForConstructorArgument(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case STREAM -> getStreamOfTypeForConstructorArgument(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case OPTIONAL -> findBeanForConstructorArgument(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case VALUE -> getValueForConstructorArgument(resolutionContext, context, index, null);
            case PROPERTY -> getPropertyValueForConstructorArgument(resolutionContext, context, index, Injection.propertyOf(argument), null);
        };
    }

    @Nullable
    @SuppressWarnings({"deprecation", "unchecked", "rawtypes", "NullAway"}) // the base class accepts a null qualifier and resolves it from the argument
    private Object resolveFieldValue(BeanResolutionContext resolutionContext, BeanContext context, int index) {
        Argument<?> argument = fieldArguments[index];
        return switch (Injection.of(argument)) {
            case BEAN -> getBeanForField(resolutionContext, context, index, qualifierOf(argument));
            case BEANS -> toCollectionType(argument, (Collection<?>) getBeansOfTypeForField(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument)));
            case REGISTRATIONS -> toCollectionType(argument, getBeanRegistrationsForField(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument)));
            case REGISTRATION -> getBeanRegistrationForField(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case MAP -> getMapOfTypeForField(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case STREAM -> getStreamOfTypeForField(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case OPTIONAL -> findBeanForField(resolutionContext, context, index, Injection.elementOf(argument), qualifierOf(argument));
            case VALUE -> getValueForField(resolutionContext, context, index, null);
            case PROPERTY -> getPropertyValueForField(resolutionContext, context, argument, Injection.propertyOf(argument), null);
        };
    }

    @Nullable
    @SuppressWarnings({"deprecation", "unchecked", "rawtypes", "NullAway"}) // the base class accepts a null qualifier and resolves it from the argument
    private Object resolveMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int index) {
        Argument<?> argument = methodArguments[methodIndex][index];
        return switch (Injection.of(argument)) {
            case BEAN -> getBeanForMethodArgument(resolutionContext, context, methodIndex, index, qualifierOf(argument));
            case BEANS -> toCollectionType(argument, getBeansOfTypeForMethodArgument(resolutionContext, context, methodIndex, index, Injection.elementOf(argument), qualifierOf(argument)));
            case REGISTRATIONS -> toCollectionType(argument, getBeanRegistrationsForMethodArgument(resolutionContext, context, methodIndex, index, Injection.elementOf(argument), qualifierOf(argument)));
            case REGISTRATION -> getBeanRegistrationForMethodArgument(resolutionContext, context, methodIndex, index, Injection.elementOf(argument), qualifierOf(argument));
            case MAP -> getMapOfTypeForMethodArgument(resolutionContext, context, methodIndex, index, Injection.elementOf(argument), qualifierOf(argument));
            case STREAM -> getStreamOfTypeForMethodArgument(resolutionContext, context, methodIndex, index, Injection.elementOf(argument), qualifierOf(argument));
            case OPTIONAL -> findBeanForMethodArgument(resolutionContext, context, methodIndex, index, Injection.elementOf(argument), qualifierOf(argument));
            case VALUE -> getValueForMethodArgument(resolutionContext, context, methodIndex, index, null);
            case PROPERTY -> getPropertyValueForMethodArgument(resolutionContext, context, methodIndex, index, Injection.propertyOf(argument), null);
        };
    }

    /**
     * Two reflective definitions are equal only when they are the same one: several definitions of one type,
     * each with its own qualifier, are as many beans.
     */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return beanDefinitionName.hashCode();
    }

    /**
     * The qualifier of an injected argument, read from its annotations as the processors read it: the
     * qualifier annotations other than {@link Primary}, composed when there are several; else an interceptor
     * binding; else the types of a {@link Type} annotation.
     */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <K> Qualifier<K> qualifierOf(Argument<?> argument) {
        AnnotationMetadata metadata = argument.getAnnotationMetadata();
        if (metadata.isEmpty()) {
            return null;
        }
        List<String> names = metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).stream()
            .filter(name -> !name.equals(Primary.NAME))
            .toList();
        if (!names.isEmpty()) {
            if (names.size() == 1) {
                return qualifierFor(argument, metadata, names.get(0));
            }
            Qualifier[] qualifiers = new Qualifier[names.size()];
            for (int i = 0; i < qualifiers.length; i++) {
                qualifiers[i] = qualifierFor(argument, metadata, names.get(i));
            }
            return Qualifiers.byQualifiers(qualifiers);
        }
        if (metadata.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
            return Qualifiers.byInterceptorBinding(metadata);
        }
        if (metadata.hasDeclaredAnnotation(Type.class)) {
            Class<?>[] byType = metadata.classValues(Type.class);
            if (byType.length > 0) {
                return Qualifiers.byType(byType);
            }
        }
        return null;
    }

    @Nullable
    private static <K> Qualifier<K> qualifierFor(Argument<?> argument, AnnotationMetadata metadata, String annotationName) {
        if (annotationName.equals(AnnotationUtil.NAMED)) {
            String name = metadata.stringValue(AnnotationUtil.NAMED).orElse(argument.getName());
            return name.contains("$") ? Qualifiers.forArgument(argument) : Qualifiers.byName(name);
        }
        if (annotationName.equals(Any.NAME)) {
            return Qualifiers.any();
        }
        String repeatableContainer = metadata.findRepeatableAnnotation(annotationName).orElse(null);
        if (repeatableContainer != null) {
            return Qualifiers.byRepeatableAnnotation(metadata, repeatableContainer);
        }
        return Qualifiers.byAnnotationSimple(metadata, annotationName);
    }

    /**
     * The beans resolved for a collection argument as the argument declares them: the arrays are converted by
     * the generated code, so the base class leaves them as collections.
     */
    @SuppressWarnings("unchecked")
    private static Object toCollectionType(Argument<?> argument, Collection<?> beans) {
        Class<?> type = argument.getType();
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), beans.size());
            int i = 0;
            for (Object bean : beans) {
                Array.set(array, i++, bean);
            }
            return array;
        }
        if (type.isInstance(beans)) {
            return beans;
        }
        Optional<Iterable<Object>> converted = CollectionUtils.convertCollection((Class<? extends Iterable<Object>>) type, (Collection<Object>) beans);
        return converted.orElseThrow(() -> new IllegalStateException("Cannot create a collection of type: " + type.getName()));
    }

    /**
     * The members read from the class, with the references the base definition is built from.
     *
     * @param constructor          The selected constructor
     * @param constructorReference The reference of the selected constructor
     * @param fields               The injected fields
     * @param fieldReferences      The references of the injected fields
     * @param methods              The injected and life cycle methods
     * @param methodReferences     The references of the injected and life cycle methods
     * @param executableMethods    The executable methods, {@code null} when there is none
     * @param <T>                  The bean type
     */
    private record Members<T>(Constructor<T> constructor,
                              MethodReference constructorReference,
                              List<Field> fields,
                              FieldReference[] fieldReferences,
                              List<Method> methods,
                              MethodReference[] methodReferences,
                              @Nullable ReflectionExecutableMethodsDefinition<T> executableMethods) {
    }

    /**
     * How an injected argument is resolved, decided from its type and annotations as the processors decide it.
     */
    private enum Injection {
        BEAN, BEANS, REGISTRATION, REGISTRATIONS, MAP, STREAM, OPTIONAL, VALUE, PROPERTY;

        private static final Set<Class<?>> MAP_TYPES = Set.of(Map.class, HashMap.class, LinkedHashMap.class, TreeMap.class);

        static Injection of(Argument<?> argument) {
            AnnotationMetadata metadata = argument.getAnnotationMetadata();
            if (metadata.hasDeclaredStereotype(Property.class)) {
                return PROPERTY;
            }
            if (metadata.hasDeclaredStereotype(Value.class)) {
                return VALUE;
            }
            Class<?> type = argument.getType();
            if (type.isArray() || Collection.class.isAssignableFrom(type)) {
                Class<?> element = type.isArray() ? type.getComponentType() : argument.getFirstTypeVariable().map(Argument::getType).orElse(null);
                if (element == null || element.isPrimitive()) {
                    return BEAN;
                }
                return BeanRegistration.class.isAssignableFrom(element) ? REGISTRATIONS : BEANS;
            }
            if (MAP_TYPES.contains(type)) {
                Argument<?>[] typeParameters = argument.getTypeParameters();
                if (typeParameters.length == 2 && CharSequence.class.isAssignableFrom(typeParameters[0].getType())) {
                    return MAP;
                }
                return BEAN;
            }
            if (Stream.class.isAssignableFrom(type)) {
                return STREAM;
            }
            if (Optional.class == type) {
                return OPTIONAL;
            }
            if (BeanRegistration.class.isAssignableFrom(type)) {
                return REGISTRATION;
            }
            return BEAN;
        }

        /**
         * The argument of the beans an argument holds: the component of an array, the element of a collection or
         * a stream, the value of a map, the bean of a registration, the value of an optional.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        static Argument elementOf(Argument<?> argument) {
            Class<?> type = argument.getType();
            if (type.isArray()) {
                Class<?> component = type.getComponentType();
                if (BeanRegistration.class.isAssignableFrom(component)) {
                    return Argument.OBJECT_ARGUMENT;
                }
                return Argument.of(component);
            }
            Argument<?>[] typeParameters = argument.getTypeParameters();
            Argument<?> element = MAP_TYPES.contains(type) && typeParameters.length == 2
                ? typeParameters[1]
                : argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (BeanRegistration.class.isAssignableFrom(element.getType()) && !BeanRegistration.class.isAssignableFrom(type)) {
                return element.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            return element;
        }

        static String propertyOf(Argument<?> argument) {
            return argument.getAnnotationMetadata().stringValue(Property.class, "name")
                .orElseThrow(() -> new IllegalStateException("Property injection of the argument '" + argument.getName() + "' requires a name"));
        }
    }

    /**
     * Builds the definition of a type, reading the class for what the builder does not set.
     *
     * @param <T> The bean type
     */
    public static final class Builder<T> {

        private final Class<T> type;
        @Nullable
        private Qualifier<T> qualifier;
        @Nullable
        private Class<? extends Annotation> scope;
        @Nullable
        private Boolean singleton;
        private Class<?>[] exposedTypes = new Class<?>[0];
        @Nullable
        private AnnotationMetadata annotationMetadata;
        @Nullable
        private Constructor<T> constructor;
        private Predicate<Method> executable = method -> false;

        private Builder(Class<T> type) {
            this.type = Objects.requireNonNull(type, "The type cannot be null");
        }

        /**
         * The qualifier of the bean, overriding the qualifier annotations of the class.
         *
         * @param qualifier The qualifier, {@code null} to read the class
         * @return This builder
         */
        public Builder<T> qualifier(@Nullable Qualifier<T> qualifier) {
            this.qualifier = qualifier;
            return this;
        }

        /**
         * The name of the bean, as a {@code @Named} qualifier.
         *
         * @param name The name, {@code null} to read the class
         * @return This builder
         */
        public Builder<T> named(@Nullable String name) {
            return qualifier(name == null ? null : Qualifiers.byName(name));
        }

        /**
         * The scope of the bean, overriding the scope annotation of the class.
         *
         * @param scope The scope annotation
         * @return This builder
         */
        public Builder<T> scope(@Nullable Class<? extends Annotation> scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Whether the bean is a singleton, overriding what the scope annotation of the class says.
         *
         * @param singleton Whether the bean is a singleton
         * @return This builder
         */
        public Builder<T> singleton(boolean singleton) {
            this.singleton = singleton;
            return this;
        }

        /**
         * Limits the types the bean is exposed as, overriding the {@code @Bean(typed = ...)} annotation of the
         * class.
         *
         * @param types The exposed types, each a super type of the bean type
         * @return This builder
         */
        public Builder<T> exposedTypes(Class<?>... types) {
            for (Class<?> exposedType : types) {
                if (!exposedType.isAssignableFrom(type)) {
                    throw new IllegalArgumentException("The bean type " + type.getName() + " does not implement " + exposedType.getName());
                }
            }
            this.exposedTypes = types;
            return this;
        }

        /**
         * The annotation metadata of the bean, replacing the one read from the annotations of the class.
         *
         * @param annotationMetadata The metadata, {@code null} to read the class
         * @return This builder
         */
        public Builder<T> annotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this.annotationMetadata = annotationMetadata;
            return this;
        }

        /**
         * The constructor the bean is instantiated with, overriding the selection from the constructors of the
         * class.
         *
         * @param constructor The constructor
         * @return This builder
         */
        public Builder<T> constructor(@Nullable Constructor<T> constructor) {
            this.constructor = constructor;
            return this;
        }

        /**
         * Makes the methods a predicate accepts executable methods of the bean, in addition to the ones
         * annotated {@link Executable}. The predicate receives the non-static, non-synthetic methods of the
         * type and its super classes.
         *
         * @param executable The predicate
         * @return This builder
         */
        public Builder<T> executableMethods(Predicate<Method> executable) {
            this.executable = Objects.requireNonNull(executable, "The predicate cannot be null");
            return this;
        }

        /**
         * Builds the definition.
         *
         * @return The definition
         * @throws IllegalArgumentException When the type cannot be a bean
         */
        public ReflectionBeanDefinition<T> build() {
            if (type.isInterface() || type.isPrimitive() || type.isArray() || type.isAnnotation() || type.isEnum()) {
                throw new IllegalArgumentException("The type " + type.getName() + " cannot be a bean: it is not a class");
            }
            if (Modifier.isAbstract(type.getModifiers())) {
                throw new IllegalArgumentException("The type " + type.getName() + " cannot be a bean: it is abstract");
            }
            if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
                throw new IllegalArgumentException("The type " + type.getName() + " cannot be a bean: it is a non-static inner class");
            }
            AnnotationMetadata metadata = annotationMetadata != null ? annotationMetadata : ReflectionAnnotations.metadataOf(type);
            Constructor<T> selected = constructor != null ? constructor : selectConstructor(type);
            if (selected == null) {
                throw new IllegalArgumentException("The type " + type.getName() + " cannot be a bean: it has no accessible constructor");
            }
            selected.trySetAccessible();
            MethodReference constructorReference = new MethodReference(
                selected.getDeclaringClass(),
                "<init>",
                ReflectionArguments.argumentsOf(selected),
                ReflectionAnnotations.metadataOf(selected));

            List<Field> fields = new ArrayList<>();
            List<Method> methods = new ArrayList<>();
            List<Method> executables = new ArrayList<>();
            List<FieldReference> fieldReferences = new ArrayList<>();
            List<MethodReference> methodReferences = new ArrayList<>();
            collectMembers(metadata, fields, fieldReferences, methods, methodReferences, executables);

            ReflectionExecutableMethodsDefinition<T> executableMethods = executables.isEmpty()
                ? null
                : new ReflectionExecutableMethodsDefinition<>(metadata, executables);

            Optional<String> scopeName = scope != null
                ? Optional.of(scope.getName())
                : metadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE);
            boolean isSingleton = singleton != null ? singleton : isSingleton(metadata);
            if (isSingleton && scopeName.isEmpty()) {
                scopeName = Optional.of(Singleton.class.getName());
            }
            PrecalculatedInfo info = new PrecalculatedInfo(
                scopeName,
                false,
                false,
                isSingleton,
                metadata.hasDeclaredStereotype(Primary.class),
                false,
                false,
                executableMethods != null && executableMethods.requiresMethodProcessing(),
                false);
            Members<T> members = new Members<>(
                selected,
                constructorReference,
                fields,
                fieldReferences.toArray(FieldReference[]::new),
                methods,
                methodReferences.toArray(MethodReference[]::new),
                executableMethods);
            return new ReflectionBeanDefinition<>(
                type,
                metadata,
                members,
                typeArgumentsOf(type),
                info,
                qualifier,
                exposedTypes.length == 0 ? Collections.emptySet() : Set.of(exposedTypes));
        }

        /**
         * The constructor as the processors select it: the one annotated {@code @Inject} or {@link Creator},
         * else the only accessible one, else the accessible one with no parameter, else the first public one.
         */
        @Nullable
        @SuppressWarnings("unchecked")
        private static <T> Constructor<T> selectConstructor(Class<T> type) {
            Constructor<?>[] declared = type.getDeclaredConstructors();
            for (Constructor<?> candidate : declared) {
                if (candidate.isAnnotationPresent(Creator.class)
                    || ReflectionAnnotations.metadataOf(candidate).hasStereotype(AnnotationUtil.INJECT)) {
                    return (Constructor<T>) candidate;
                }
            }
            List<Constructor<?>> accessible = new ArrayList<>(declared.length);
            for (Constructor<?> candidate : declared) {
                if (!Modifier.isPrivate(candidate.getModifiers()) && !candidate.isSynthetic()) {
                    accessible.add(candidate);
                }
            }
            if (accessible.size() == 1) {
                return (Constructor<T>) accessible.get(0);
            }
            for (Constructor<?> candidate : accessible) {
                if (candidate.getParameterCount() == 0) {
                    return (Constructor<T>) candidate;
                }
            }
            for (Constructor<?> candidate : accessible) {
                if (Modifier.isPublic(candidate.getModifiers())) {
                    return (Constructor<T>) candidate;
                }
            }
            return null;
        }

        private static boolean isSingleton(AnnotationMetadata metadata) {
            if (metadata.hasDeclaredStereotype(AnnotationUtil.SINGLETON)) {
                return true;
            }
            return !metadata.hasDeclaredStereotype(AnnotationUtil.SCOPE)
                && metadata.hasDeclaredStereotype(DefaultScope.class)
                && metadata.stringValue(DefaultScope.class)
                .map(scope -> scope.equals(Singleton.class.getName()) || scope.equals(AnnotationUtil.SINGLETON))
                .orElse(false);
        }

        /**
         * Reads the members of the type and its super classes, the super classes first, keeping for a method
         * the declaration nearest to the type.
         */
        private void collectMembers(AnnotationMetadata typeMetadata,
                                    List<Field> fields,
                                    List<FieldReference> fieldReferences,
                                    List<Method> methods,
                                    List<MethodReference> methodReferences,
                                    List<Method> executables) {
            List<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                hierarchy.add(0, current);
            }
            Set<String> seen = new HashSet<>();
            List<Method> candidates = new ArrayList<>();
            for (Class<?> current : hierarchy) {
                for (Field field : current.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
                        continue;
                    }
                    AnnotationMetadata fieldMetadata = ReflectionAnnotations.metadataOf(field);
                    if (fieldMetadata.hasStereotype(Value.class)
                        || fieldMetadata.hasStereotype(Property.class)
                        || fieldMetadata.hasStereotype(AnnotationUtil.INJECT)) {
                        field.trySetAccessible();
                        fields.add(field);
                        fieldReferences.add(new FieldReference(field.getDeclaringClass(), ReflectionArguments.of(field)));
                    }
                }
            }
            // the methods nearest to the type first, so that an overriding declaration hides the overridden one
            for (int i = hierarchy.size() - 1; i >= 0; i--) {
                for (Method method : hierarchy.get(i).getDeclaredMethods()) {
                    if (method.isSynthetic() || method.isBridge() || !seen.add(signature(method))) {
                        continue;
                    }
                    candidates.add(method);
                }
            }
            // then in the order the processors visit them: the super classes first
            Collections.reverse(candidates);
            boolean typeExecutable = typeMetadata.hasStereotype(Executable.class);
            for (Method method : candidates) {
                AnnotationMetadata methodMetadata = ReflectionAnnotations.metadataOf(method);
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                if (!isStatic && methodMetadata.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
                    addInjectedMethod(methods, methodReferences, method, methodMetadata, true, false);
                } else if (!isStatic && methodMetadata.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
                    addInjectedMethod(methods, methodReferences, method, methodMetadata, false, true);
                } else if (!isStatic && methodMetadata.hasDeclaredStereotype(AnnotationUtil.INJECT)) {
                    addInjectedMethod(methods, methodReferences, method, methodMetadata, false, false);
                } else if (!isStatic && isSetter(method) && methodMetadata.hasStereotype(AnnotationUtil.QUALIFIER)) {
                    addInjectedMethod(methods, methodReferences, method, methodMetadata, false, false);
                } else if (methodMetadata.hasDeclaredStereotype(Executable.class)
                    || (!isStatic && typeExecutable && Modifier.isPublic(method.getModifiers()))
                    || (!isStatic && executable.test(method))) {
                    executables.add(method);
                }
            }
        }

        private static void addInjectedMethod(List<Method> methods,
                                              List<MethodReference> methodReferences,
                                              Method method,
                                              AnnotationMetadata methodMetadata,
                                              boolean postConstruct,
                                              boolean preDestroy) {
            method.trySetAccessible();
            Argument<?>[] arguments = ReflectionArguments.argumentsOf(method);
            if (arguments.length == 1 && !methodMetadata.isEmpty()) {
                // a setter is annotated on the method: the annotations bind its one parameter, as they do for a
                // processed setter
                Argument<?> parameter = arguments[0];
                AnnotationMetadata parameterMetadata = parameter.getAnnotationMetadata();
                arguments = new Argument[]{Argument.of(parameter.getType(), parameter.getName(),
                    parameterMetadata.isEmpty() ? methodMetadata : new AnnotationMetadataHierarchy(methodMetadata, parameterMetadata),
                    parameter.getTypeParameters())};
            }
            methods.add(method);
            methodReferences.add(new MethodReference(method.getDeclaringClass(), method.getName(), arguments, methodMetadata, postConstruct, preDestroy));
        }

        private static boolean isSetter(Method method) {
            String name = method.getName();
            return method.getParameterCount() == 1 && name.length() > 3 && name.startsWith("set") && Character.isUpperCase(name.charAt(3));
        }

        private static String signature(Method method) {
            StringBuilder signature = new StringBuilder(method.getName()).append('(');
            for (Class<?> parameterType : method.getParameterTypes()) {
                signature.append(parameterType.getName()).append(',');
            }
            return signature.append(')').toString();
        }

        /**
         * The type arguments the type gives to each of its generic super types, by the name of the super type.
         */
        private static Map<String, Argument<?>[]> typeArgumentsOf(Class<?> type) {
            Map<String, Argument<?>[]> typeArguments = new LinkedHashMap<>();
            for (Class<?> superType : ClassUtils.resolveHierarchy(type)) {
                if (superType == type || superType == Object.class || superType.getTypeParameters().length == 0) {
                    continue;
                }
                Argument<?> resolved = ReflectionArguments.resolveGenericToArgument(type, superType);
                if (resolved != null && resolved.getTypeParameters().length > 0) {
                    typeArguments.put(superType.getName(), resolved.getTypeParameters());
                }
            }
            return typeArguments;
        }
    }
}
