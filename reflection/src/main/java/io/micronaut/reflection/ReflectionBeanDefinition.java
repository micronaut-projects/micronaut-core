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
import io.micronaut.context.annotation.Bean;
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
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
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
 *     <li>the bean is instantiated by the static method annotated {@link Creator} the class declares, else by
 *     one of its accessible - non-private - constructors: the only one, else the one annotated
 *     {@code @Inject}, else the one annotated {@link Creator}, else the canonical constructor of a record,
 *     else the first public one. That is the selection {@code ClassElement#getPrimaryConstructor()} makes at
 *     compilation time, but for a record whose constructors carry no annotation: the processors take the
 *     first public one there, where this takes the canonical one, which is the one the components describe.
 *     An annotation counts through its stereotypes, so a meta-annotated {@code @Creator} counts;</li>
 *     <li>the non-static, non-final fields annotated {@code @Inject}, {@link Value} or {@link Property}, and
 *     the ones declaring a qualifier without {@code @Inject}, are injected, the super classes' first;</li>
 *     <li>the methods annotated {@code @Inject} and the setters annotated with a qualifier - {@link Value} is
 *     one - are injected, the methods annotated {@code @PostConstruct} and {@code @PreDestroy} are the life cycle
 *     methods;</li>
 *     <li>the methods annotated {@link Executable}, and the public methods of a type annotated {@link Executable},
 *     are the executable methods;</li>
 *     <li>the scope, the qualifier, the order, the exposed types of {@code @Bean(typed = ...)} and the
 *     conditions come from the annotations of the class, unless the {@link Builder builder} overrides them.</li>
 * </ul>
 *
 * <p>An inherited injection point is read as the bean type sees it: a member a generic super class declares
 * over a variable that the bean type gives a value to - a {@code T dep} of a {@code class Base<T>} inherited by
 * a {@code class Impl extends Base<Book>} - is injected as the value, a {@code Book}, which is what a generated
 * definition of the bean type asks for.</p>
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
    @Nullable
    private final Constructor<T> constructor;
    @Nullable
    private final Method factoryMethod;
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
        this.factoryMethod = members.factoryMethod;
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
     * @return The constructor, {@code null} when the bean is instantiated by a {@link Creator} factory method
     * @see #getTargetFactoryMethod()
     */
    @Nullable
    public Constructor<T> getTargetConstructor() {
        return constructor;
    }

    /**
     * The static {@link Creator} method the bean is instantiated with, when the class declares one.
     *
     * @return The factory method, {@code null} when the bean is instantiated by a constructor
     * @see #getTargetConstructor()
     */
    @Nullable
    public Method getTargetFactoryMethod() {
        return factoryMethod;
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
    @SuppressWarnings("unchecked")
    public T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        Object[] arguments = new Object[constructorArguments.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = resolveConstructorArgument(resolutionContext, context, i);
        }
        T bean;
        try {
            if (factoryMethod == null) {
                bean = Objects.requireNonNull(constructor).newInstance(arguments);
            } else {
                // a static factory is the instantiation route of a class that keeps its constructors to itself
                Object created = factoryMethod.invoke(null, arguments);
                if (created == null) {
                    throw new BeanInstantiationException(resolutionContext, "The factory method '" + factoryMethod.getName() + "' returned null");
                }
                bean = (T) created;
            }
        } catch (InvocationTargetException e) {
            throw new BeanInstantiationException(resolutionContext, e.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new BeanInstantiationException(resolutionContext, e);
        }
        return initialize(resolutionContext, context, inject(resolutionContext, context, bean));
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
     * @param constructor          The selected constructor, {@code null} when a factory method instantiates
     * @param factoryMethod        The selected static factory method, {@code null} when a constructor
     *                             instantiates
     * @param constructorReference The reference of the selected constructor or factory method
     * @param fields               The injected fields
     * @param fieldReferences      The references of the injected fields
     * @param methods              The injected and life cycle methods
     * @param methodReferences     The references of the injected and life cycle methods
     * @param executableMethods    The executable methods, {@code null} when there is none
     * @param <T>                  The bean type
     */
    private record Members<T>(@Nullable Constructor<T> constructor,
                              @Nullable Method factoryMethod,
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
        private AnnotationMetadata additionalAnnotationMetadata = AnnotationMetadata.EMPTY_METADATA;
        @Nullable
        private Constructor<T> constructor;
        private Predicate<Method> executable = method -> false;
        private Set<String> postConstruct = Set.of();
        private Set<String> preDestroy = Set.of();

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
         * The annotations the bean carries beyond the ones its class declares, both of them declared: a
         * container adapting another one - a Spring bean it marks primary, a Guice binding it qualifies - adds
         * its own without losing what the class says.
         *
         * @param annotationMetadata The annotations to add
         * @return This builder
         */
        public Builder<T> additionalAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this.additionalAnnotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            return this;
        }

        /**
         * The methods to invoke once the bean is created and injected, named rather than annotated: a
         * container that names them - the {@code initMethod} of a Spring bean definition - has no annotation to
         * read. The methods annotated {@code @PostConstruct} are invoked as well, and the arguments of a named
         * method are injected as those of an annotated one are.
         *
         * @param methodNames The method names
         * @return This builder
         */
        public Builder<T> postConstruct(String... methodNames) {
            this.postConstruct = Set.of(methodNames);
            return this;
        }

        /**
         * The methods to invoke when the bean is destroyed, named rather than annotated.
         *
         * @param methodNames The method names
         * @return This builder
         * @see #postConstruct(String...)
         */
        public Builder<T> preDestroy(String... methodNames) {
            this.preDestroy = Set.of(methodNames);
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
            // the annotations the caller means the bean to carry win where both declare the same one: a
            // container adapting another one says what that one says, over what the class says of itself
            AnnotationMetadata metadata = ReflectionAnnotations.merge(
                additionalAnnotationMetadata,
                annotationMetadata != null ? annotationMetadata : ReflectionAnnotations.metadataOf(type));
            Method factory = null;
            Constructor<T> selected = constructor;
            if (selected == null) {
                // a static `@Creator` factory is the instantiation route the processors select first
                factory = selectFactoryMethod(type);
                if (factory == null) {
                    selected = selectConstructor(type);
                }
            }
            MethodReference constructorReference;
            if (selected != null) {
                open(selected);
                constructorReference = new MethodReference(
                    selected.getDeclaringClass(),
                    "<init>",
                    ReflectionArguments.argumentsOf(selected, type),
                    ReflectionAnnotations.metadataOf(selected));
            } else if (factory != null) {
                open(factory);
                // a factory method is referenced by its name, as a generated definition references the factory
                // method of the bean it produces
                constructorReference = new MethodReference(
                    factory.getDeclaringClass(),
                    factory.getName(),
                    ReflectionArguments.argumentsOf(factory, type),
                    ReflectionAnnotations.metadataOf(factory));
            } else {
                throw new IllegalArgumentException("The type " + type.getName() + " cannot be a bean: it has no accessible constructor");
            }

            List<Field> fields = new ArrayList<>();
            List<Method> methods = new ArrayList<>();
            List<Method> executables = new ArrayList<>();
            List<FieldReference> fieldReferences = new ArrayList<>();
            List<MethodReference> methodReferences = new ArrayList<>();
            collectMembers(metadata, fields, fieldReferences, methods, methodReferences, executables);

            ReflectionExecutableMethodsDefinition<T> executableMethods = executables.isEmpty()
                ? null
                : new ReflectionExecutableMethodsDefinition<>(metadata, executables, type);

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
                factory,
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
                exposedTypes.length == 0 ? exposedTypesOf(metadata) : Set.of(exposedTypes));
        }

        /**
         * The types the {@code @Bean(typed = ...)} annotation of the class exposes the bean as, which the
         * builder overrides when it sets any: the base definition answers no exposed type, so the annotation
         * is read here as {@link io.micronaut.inject.BeanType#getExposedTypes()} reads it for a generated one.
         */
        private static Set<Class<?>> exposedTypesOf(AnnotationMetadata metadata) {
            if (!metadata.hasDeclaredAnnotation(Bean.class)) {
                return Collections.emptySet();
            }
            Class<?>[] typed = metadata.classValues(Bean.class, "typed");
            return typed.length == 0 ? Collections.emptySet() : Collections.unmodifiableSet(CollectionUtils.setOf(typed));
        }

        /**
         * The constructor as the processors select it - the selection of
         * {@code ClassElement#getPrimaryConstructor()}: among the accessible constructors, the only one, else
         * the one annotated {@code @Inject}, else the one annotated {@link Creator}, else the canonical
         * constructor of a record, else the first public one. A private constructor is not selected, and an
         * annotation counts through its stereotypes.
         *
         * <p>Two places read differently from the compilation time selection, both where it has nothing to
         * read: it filters the constructors annotated {@code @Inject} or {@link Creator} in one pass and
         * takes the first, where this prefers {@code @Inject}; and it has no record branch, so for a record
         * whose constructors carry no annotation it takes the first public one where this takes the
         * canonical one.</p>
         */
        @Nullable
        @SuppressWarnings("unchecked")
        private static <T> Constructor<T> selectConstructor(Class<T> type) {
            List<Constructor<?>> accessible = new ArrayList<>();
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                if (!Modifier.isPrivate(candidate.getModifiers()) && !candidate.isSynthetic()) {
                    accessible.add(candidate);
                }
            }
            if (accessible.isEmpty()) {
                return null;
            }
            if (accessible.size() == 1) {
                return (Constructor<T>) accessible.get(0);
            }
            Constructor<?> annotated = annotatedWith(accessible, AnnotationUtil.INJECT);
            if (annotated == null) {
                annotated = annotatedWith(accessible, Creator.class.getName());
            }
            if (annotated != null) {
                return (Constructor<T>) annotated;
            }
            if (type.isRecord()) {
                // with nothing annotated, the canonical constructor is the one a record is built by; another
                // one it declares delegates to it
                Class<?>[] components = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getType)
                    .toArray(Class<?>[]::new);
                for (Constructor<?> candidate : accessible) {
                    if (Arrays.equals(candidate.getParameterTypes(), components)) {
                        return (Constructor<T>) candidate;
                    }
                }
            }
            for (Constructor<?> candidate : accessible) {
                if (Modifier.isPublic(candidate.getModifiers())) {
                    return (Constructor<T>) candidate;
                }
            }
            return null;
        }

        @Nullable
        private static Constructor<?> annotatedWith(List<Constructor<?>> constructors, String annotation) {
            for (Constructor<?> candidate : constructors) {
                if (ReflectionAnnotations.metadataOf(candidate).hasStereotype(annotation)) {
                    return candidate;
                }
            }
            return null;
        }

        /**
         * The static factory method as the processors select it - the selection of
         * {@code ClassElement#findStaticCreator()}: an accessible static method the type itself declares,
         * annotated {@link Creator} through its metadata and returning the type or a sub type of it. When the
         * type declares several, the only one taking parameters, else the first public one.
         */
        @Nullable
        private static Method selectFactoryMethod(Class<?> type) {
            List<Method> creators = new ArrayList<>();
            for (Method candidate : type.getDeclaredMethods()) {
                int modifiers = candidate.getModifiers();
                if (!Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || candidate.isSynthetic()) {
                    continue;
                }
                if (type.isAssignableFrom(candidate.getReturnType())
                    && ReflectionAnnotations.metadataOf(candidate).hasStereotype(Creator.class)) {
                    creators.add(candidate);
                }
            }
            if (creators.size() < 2) {
                return creators.isEmpty() ? null : creators.get(0);
            }
            // a no-argument factory loses to one taking parameters, as it does at compilation time
            List<Method> withArguments = creators.stream().filter(candidate -> candidate.getParameterCount() > 0).toList();
            if (withArguments.size() == 1) {
                return withArguments.get(0);
            }
            return withArguments.stream()
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .findFirst()
                .orElse(null);
        }

        /**
         * Opens a member for reflective access, failing at build time - rather than at injection time, with a
         * {@link DependencyInjectionException} the caller cannot act on - when the module declaring the type
         * does not open its package.
         */
        private static <M extends AccessibleObject & Member> void open(M member) {
            if (!member.trySetAccessible()) {
                Class<?> declaringType = member.getDeclaringClass();
                throw new IllegalArgumentException("The type " + declaringType.getName() + " cannot be a bean: its member '"
                    + member.getName() + "' cannot be made accessible, the " + declaringType.getModule()
                    + " does not open the package " + declaringType.getPackageName() + " for reflection");
            }
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
                    // a field declaring a qualifier is injected without `@Inject`, as the processors inject it
                    if (fieldMetadata.hasStereotype(Value.class)
                        || fieldMetadata.hasStereotype(Property.class)
                        || fieldMetadata.hasStereotype(AnnotationUtil.INJECT)
                        || fieldMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)) {
                        open(field);
                        fields.add(field);
                        fieldReferences.add(new FieldReference(field.getDeclaringClass(), ReflectionArguments.of(field, type)));
                    }
                }
            }
            // the methods nearest to the type first, so that an overriding declaration hides the overridden one
            for (int i = hierarchy.size() - 1; i >= 0; i--) {
                Method[] declared = hierarchy.get(i).getDeclaredMethods();
                for (Method method : declared) {
                    if (method.isSynthetic() || method.isBridge() || !seen.add(signature(method))) {
                        continue;
                    }
                    candidates.add(method);
                }
                // then the bridges the class declares: a bridge carries the erased signature of the generic
                // declaration its target overrides, so recording it hides that declaration, which the
                // processors treat as overridden rather than as a second injection point
                for (Method method : declared) {
                    if (method.isBridge()) {
                        seen.add(signature(method));
                    }
                }
            }
            // then in the order the processors visit them: the super classes first
            Collections.reverse(candidates);
            boolean typeExecutable = typeMetadata.hasStereotype(Executable.class);
            for (Method method : candidates) {
                AnnotationMetadata methodMetadata = ReflectionAnnotations.metadataOf(method);
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                if (!isStatic && (methodMetadata.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT) || postConstruct.contains(method.getName()))) {
                    addInjectedMethod(methods, methodReferences, method, methodMetadata, true, false);
                } else if (!isStatic && (methodMetadata.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY) || preDestroy.contains(method.getName()))) {
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

        private void addInjectedMethod(List<Method> methods,
                                       List<MethodReference> methodReferences,
                                       Method method,
                                       AnnotationMetadata methodMetadata,
                                       boolean postConstruct,
                                       boolean preDestroy) {
            open(method);
            Argument<?>[] arguments = ReflectionArguments.argumentsOf(method, type);
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
