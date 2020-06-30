/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation.validator;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentValue;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.validation.BeanDefinitionValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;
import io.micronaut.validation.validator.extractors.SimpleValueReceiver;
import io.micronaut.validation.validator.extractors.ValueExtractorRegistry;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.*;
import javax.validation.groups.Default;
import javax.validation.metadata.*;
import javax.validation.valueextraction.ValueExtractor;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link Validator} interface.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Primary
@Requires(property = ValidatorConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class DefaultValidator implements Validator, ExecutableMethodValidator, ReactiveValidator, AnnotatedElementValidator, BeanDefinitionValidator {

    private static final List<Class> DEFAULT_GROUPS = Collections.singletonList(Default.class);
    private final ConstraintValidatorRegistry constraintValidatorRegistry;
    private final ClockProvider clockProvider;
    private final ValueExtractorRegistry valueExtractorRegistry;
    private final TraversableResolver traversableResolver;
    private final ExecutionHandleLocator executionHandleLocator;
    private final MessageSource messageSource;

    /**
     * Default constructor.
     *
     * @param configuration The validator configuration
     */
    @Inject
    protected DefaultValidator(
            @NonNull ValidatorConfiguration configuration) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        this.constraintValidatorRegistry = configuration.getConstraintValidatorRegistry();
        this.clockProvider = configuration.getClockProvider();
        this.valueExtractorRegistry = configuration.getValueExtractorRegistry();
        this.traversableResolver = configuration.getTraversableResolver();
        this.executionHandleLocator = configuration.getExecutionHandleLocator();
        this.messageSource = configuration.getMessageSource();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validate(@NonNull T object, @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        final BeanIntrospection<T> introspection = (BeanIntrospection<T>) getBeanIntrospection(object);
        if (introspection == null) {
            return Collections.emptySet();
        }
        return validate(introspection, object, groups);
    }

    /**
     * Validate the given introspection and object.
     * @param introspection The introspection
     * @param object The object
     * @param groups The groups
     * @param <T> The object type
     * @return The constraint violations
     */
    @Override
    @SuppressWarnings("ConstantConditions")
    @NonNull
    public <T> Set<ConstraintViolation<T>> validate(@NonNull BeanIntrospection<T> introspection, @NonNull T object, @Nullable Class<?>... groups) {
        if (introspection == null) {
            throw new ValidationException("Passed object [" + object + "] cannot be introspected. Please annotate with @Introspected");
        }
        @SuppressWarnings("unchecked")
        final Collection<? extends BeanProperty<Object, Object>> constrainedProperties =
                ((BeanIntrospection<Object>) introspection).getIndexedProperties(Constraint.class);
        @SuppressWarnings("unchecked")
        final Collection<BeanProperty<Object, Object>> cascadeProperties =
                ((BeanIntrospection<Object>) introspection).getIndexedProperties(Valid.class);

        final List<Class<? extends Annotation>> pojoConstraints = introspection.getAnnotationTypesByStereotype(Constraint.class);

        if (CollectionUtils.isNotEmpty(constrainedProperties)
                || CollectionUtils.isNotEmpty(cascadeProperties)
                || CollectionUtils.isNotEmpty(pojoConstraints)) {

            DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object, groups);
            Set<ConstraintViolation<T>> overallViolations = new HashSet<>(5);
            return doValidate(
                    introspection,
                    object,
                    object,
                    constrainedProperties,
                    cascadeProperties,
                    context,
                    overallViolations,
                    pojoConstraints
            );
        }
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(
            @NonNull T object,
            @NonNull String propertyName,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        ArgumentUtils.requireNonNull("propertyName", propertyName);
        final BeanIntrospection<Object> introspection = getBeanIntrospection(object);
        if (introspection == null) {
            throw new ValidationException("Passed object [" + object + "] cannot be introspected. Please annotate with @Introspected");
        }

        final Optional<BeanProperty<Object, Object>> property = introspection.getProperty(propertyName);

        if (property.isPresent()) {
            final BeanProperty<Object, Object> constrainedProperty = property.get();
            DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object, groups);
            Set overallViolations = new HashSet<>(5);
            final Object propertyValue = constrainedProperty.get(object);

            @SuppressWarnings("unchecked")
            final Class<T> rootBeanClass = (Class<T>) object.getClass();
            //noinspection unchecked
            validateConstrainedPropertyInternal(
                    rootBeanClass,
                    object,
                    object,
                    constrainedProperty,
                    constrainedProperty.getType(),
                    propertyValue,
                    context,
                    overallViolations,
                    null);

            //noinspection unchecked
            return Collections.unmodifiableSet(overallViolations);
        }

        return Collections.emptySet();
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(
            @NonNull Class<T> beanType,
            @NonNull String propertyName,
            @Nullable Object value,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("propertyName", propertyName);

        final BeanIntrospection<Object> introspection = getBeanIntrospection(beanType);
        if (introspection == null) {
            throw new ValidationException("Passed bean type [" + beanType + "] cannot be introspected. Please annotate with @Introspected");
        }

        final BeanProperty<Object, Object> beanProperty = introspection.getProperty(propertyName)
                .orElseThrow(() -> new ValidationException("No property [" + propertyName + "] found on type: " + beanType));


        final HashSet overallViolations = new HashSet<>(5);
        final DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(groups);
        try {
            context.addPropertyNode(propertyName, null);
            //noinspection unchecked
            validatePropertyInternal(
                    beanType,
                    null,
                    null,
                    context,
                    overallViolations,
                    beanProperty.getType(),
                    beanProperty,
                    value);
        } finally {
            context.removeLast();
        }

        //noinspection unchecked
        return Collections.unmodifiableSet(overallViolations);
    }

    @NonNull
    @Override
    public Set<String> validatedAnnotatedElement(@NonNull AnnotatedElement element, @Nullable Object value) {
        ArgumentUtils.requireNonNull("element", element);
        if (!element.getAnnotationMetadata().hasStereotype(Constraint.class)) {
            return Collections.emptySet();
        }

        final Set<ConstraintViolation<Object>> overallViolations = new HashSet<>(5);
        final DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext();
        try {
            context.addPropertyNode(element.getName(), null);
            //noinspection unchecked
            validatePropertyInternal(
                    null,
                    element,
                    element,
                    context,
                    overallViolations,
                    value != null ? value.getClass() : Object.class,
                    element,
                    value);
        } finally {
            context.removeLast();
        }

        //noinspection unchecked
        return Collections.unmodifiableSet(overallViolations.stream()
                .map(ConstraintViolation::getMessage).collect(Collectors.toSet()));
    }

    @NonNull
    @Override
    public <T> T createValid(@NonNull Class<T> beanType, Object... arguments) throws ConstraintViolationException {
        ArgumentUtils.requireNonNull("type", beanType);

        @SuppressWarnings("unchecked")
        final BeanIntrospection<T> introspection = (BeanIntrospection<T>) getBeanIntrospection(beanType);
        if (introspection == null) {
            throw new ValidationException("Passed bean type [" + beanType + "] cannot be introspected. Please annotate with @Introspected");
        }

        final Set<ConstraintViolation<T>> constraintViolations = validateConstructorParameters(introspection, arguments);

        if (constraintViolations.isEmpty()) {
            final T instance = introspection.instantiate(arguments);
            final Set<ConstraintViolation<T>> errors = validate(introspection, instance);
            if (errors.isEmpty()) {
                return instance;
            } else {
                throw new ConstraintViolationException(errors);
            }
        }

        throw new ConstraintViolationException(constraintViolations);
    }

    @Override
    public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
        return BeanIntrospector.SHARED.findIntrospection(clazz)
                .map((Function<BeanIntrospection<?>, BeanDescriptor>) IntrospectedBeanDescriptor::new)
                .orElseGet(() -> new EmptyDescriptor(clazz));
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        throw new UnsupportedOperationException("Validator unwrapping not supported by this implementation");
    }

    @Override
    @NonNull
    public ExecutableMethodValidator forExecutables() {
        return this;
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateParameters(
            @NonNull T object,
            @NonNull ExecutableMethod method,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("parameterValues", parameterValues);
        ArgumentUtils.requireNonNull("object", object);
        ArgumentUtils.requireNonNull("method", method);
        final Argument[] arguments = method.getArguments();
        final int argLen = arguments.length;
        if (argLen != parameterValues.length) {
            throw new IllegalArgumentException("The method parameter array must have exactly " + argLen + " elements.");
        }

        DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object, groups);
        Set overallViolations = new HashSet<>(5);

        final Path.Node node = context.addMethodNode(method);
        try {
            @SuppressWarnings("unchecked")
            final Class<T> rootClass = (Class<T>) object.getClass();
            validateParametersInternal(
                    rootClass,
                    object,
                    parameterValues,
                    arguments,
                    argLen,
                    context,
                    overallViolations,
                    node
            );
        } finally {
            context.removeLast();
        }
        //noinspection unchecked
        return Collections.unmodifiableSet(overallViolations);
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateParameters(
            @NonNull T object, @NonNull
            ExecutableMethod method,
            @NonNull Collection<MutableArgumentValue<?>> argumentValues,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        ArgumentUtils.requireNonNull("method", method);
        ArgumentUtils.requireNonNull("parameterValues", argumentValues);
        final Argument[] arguments = method.getArguments();
        final int argLen = arguments.length;
        if (argLen != argumentValues.size()) {
            throw new IllegalArgumentException("The method parameter array must have exactly " + argLen + " elements.");
        }

        DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object, groups);
        Set overallViolations = new HashSet<>(5);

        final Path.Node node = context.addMethodNode(method);
        try {
            @SuppressWarnings("unchecked")
            final Class<T> rootClass = (Class<T>) object.getClass();
            validateParametersInternal(
                    rootClass,
                    object,
                    argumentValues.stream().map(ArgumentValue::getValue).toArray(),
                    arguments,
                    argLen,
                    context,
                    overallViolations,
                    node
            );
        } finally {
            context.removeLast();
        }
        //noinspection unchecked
        return Collections.unmodifiableSet(overallViolations);
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateParameters(
            @NonNull T object,
            @NonNull Method method,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("method", method);
        return executionHandleLocator.findExecutableMethod(
                method.getDeclaringClass(),
                method.getName(),
                method.getParameterTypes()
        ).map(executableMethod ->
                validateParameters(object, executableMethod, parameterValues, groups)
        ).orElse(Collections.emptySet());
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateReturnValue(
            @NonNull T object,
            @NonNull Method method,
            @Nullable Object returnValue,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("method", method);
        ArgumentUtils.requireNonNull("object", object);
        return executionHandleLocator.findExecutableMethod(
                method.getDeclaringClass(),
                method.getName(),
                method.getParameterTypes()
        ).map(executableMethod ->
                validateReturnValue(object, executableMethod, returnValue, groups)
        ).orElse(Collections.emptySet());
    }

    @Override
    public @NonNull <T> Set<ConstraintViolation<T>> validateReturnValue(
            @NonNull T object,
            @NonNull ExecutableMethod<?, Object> executableMethod,
            @Nullable Object returnValue,
            @Nullable Class<?>... groups) {
        final ReturnType<Object> returnType = executableMethod.getReturnType();
        final Argument<Object> returnTypeArgument = returnType.asArgument();
        final HashSet overallViolations = new HashSet(3);
        @SuppressWarnings("unchecked")
        final Class<T> rootBeanClass = (Class<T>) object.getClass();
        final DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object, groups);
        //noinspection unchecked
        validateConstrainedPropertyInternal(
                rootBeanClass,
                object,
                object,
                returnTypeArgument,
                returnType.getType(),
                returnValue,
                context,
                overallViolations,
                null
        );

        final AnnotationMetadata annotationMetadata = returnTypeArgument.getAnnotationMetadata();
        final boolean hasValid = annotationMetadata.isAnnotationPresent(Valid.class);

        if (hasValid) {
            validateCascadePropertyInternal(context,
                    rootBeanClass,
                    object,
                    object,
                    returnTypeArgument,
                    returnValue,
                    overallViolations);
        }

        //noinspection unchecked
        return overallViolations;
    }

    private <T> void validateCascadePropertyInternal(DefaultConstraintValidatorContext context,
                                                     @NonNull Class<T> rootBeanClass,
                                                     @Nullable T rootBean,
                                                     Object object,
                                                     @NonNull Argument<?> cascadeProperty,
                                                     @Nullable Object propertyValue,
                                                     Set overallViolations) {
        if (propertyValue != null) {
            @SuppressWarnings("unchecked") final Optional<? extends ValueExtractor<Object>> opt = valueExtractorRegistry
                    .findValueExtractor((Class<Object>) cascadeProperty.getType());

            opt.ifPresent(valueExtractor -> valueExtractor.extractValues(propertyValue, new ValueExtractor.ValueReceiver() {
                @Override
                public void value(String nodeName, Object object1) {

                }

                @Override
                public void iterableValue(String nodeName, Object iterableValue) {
                    if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                        return;
                    }
                    cascadeToIterableValue(
                            context,
                            rootBeanClass,
                            rootBean,
                            object,
                            null,
                            cascadeProperty,
                            iterableValue,
                            overallViolations,
                            null,
                            null,
                            true);
                }

                @Override
                public void indexedValue(String nodeName, int i, Object iterableValue) {
                    if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                        return;
                    }
                    cascadeToIterableValue(
                            context,
                            rootBeanClass,
                            rootBean,
                            object,
                            null,
                            cascadeProperty,
                            iterableValue,
                            overallViolations,
                            i,
                            null,
                            true);
                }

                @Override
                public void keyedValue(String nodeName, Object key, Object keyedValue) {
                    if (keyedValue != null && context.validatedObjects.contains(keyedValue)) {
                        return;
                    }
                    cascadeToIterableValue(
                            context,
                            rootBeanClass,
                            rootBean,
                            object,
                            null,
                            cascadeProperty,
                            keyedValue,
                            overallViolations,
                            null,
                            key,
                            false
                    );
                }
            }));

            if (!opt.isPresent() && !context.validatedObjects.contains(propertyValue)) {
                try {
                    // maybe a bean
                    final Path.Node node = context.addReturnValueNode(cascadeProperty.getName());
                    final boolean canCascade = canCascade(rootBeanClass, context, propertyValue, node);
                    if (canCascade) {
                        cascadeToOne(
                                rootBeanClass,
                                rootBean,
                                object,
                                context,
                                overallViolations,
                                cascadeProperty,
                                cascadeProperty.getType(),
                                propertyValue,
                                null);
                    }
                } finally {
                    context.removeLast();
                }
            }
        }
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            @NonNull Constructor<? extends T> constructor,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("constructor", constructor);
        final Class<? extends T> declaringClass = constructor.getDeclaringClass();
        final BeanIntrospection<? extends T> introspection = BeanIntrospection.getIntrospection(declaringClass);
        return validateConstructorParameters(introspection, parameterValues);
    }

    @Override
    @NonNull
    public <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            @NonNull BeanIntrospection<? extends T> introspection,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("introspection", introspection);
        final Class<? extends T> beanType = introspection.getBeanType();
        final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        return validateConstructorParameters(beanType, constructorArguments, parameterValues, groups);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorParameters(Class<? extends T> beanType, Argument<?>[] constructorArguments, @NonNull Object[] parameterValues, @Nullable Class<?>[] groups) {
        //noinspection ConstantConditions
        parameterValues = parameterValues != null ? parameterValues : ArrayUtils.EMPTY_OBJECT_ARRAY;
        final int argLength = constructorArguments.length;
        if (parameterValues.length != argLength) {
            throw new IllegalArgumentException("Expected exactly [" + argLength + "] constructor arguments");
        }
        DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(groups);
        Set overallViolations = new HashSet<>(5);

        final Path.Node node = context.addConstructorNode(beanType.getSimpleName(), constructorArguments);
        try {
            validateParametersInternal(
                    beanType,
                    null,
                    parameterValues,
                    constructorArguments,
                    argLength,
                    context,
                    overallViolations,
                    node);
        } finally {
            context.removeLast();
        }
        //noinspection unchecked
        return Collections.unmodifiableSet(overallViolations);
    }

    @NonNull
    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(@NonNull Constructor<? extends T> constructor, @NonNull T createdObject, @Nullable Class<?>... groups) {
        return validate(createdObject, groups);
    }


    /**
     * looks up a bean introspection for the given object by instance's class or defined class.
     *
     * @param object The object, never null
     * @param definedClass The defined class of the object, never null
     * @return The introspection or null
     */
    @SuppressWarnings({"WeakerAccess", "unchecked"})
    protected @Nullable BeanIntrospection<Object> getBeanIntrospection(@NonNull Object object, @NonNull Class<?> definedClass) {
        //noinspection ConstantConditions
        if (object == null) {
            return null;
        }
        return BeanIntrospector.SHARED.findIntrospection((Class<Object>) object.getClass())
                .orElseGet(() -> BeanIntrospector.SHARED.findIntrospection((Class<Object>) definedClass).orElse(null));
    }

    /**
     * looks up a bean introspection for the given object.
     *
     * @param object The object, never null
     * @return The introspection or null
     */
    @SuppressWarnings({"WeakerAccess", "unchecked"})
    protected @Nullable BeanIntrospection<Object> getBeanIntrospection(@NonNull Object object) {
        //noinspection ConstantConditions
        if (object == null) {
            return null;
        }
        if (object instanceof Class) {
            return BeanIntrospector.SHARED.findIntrospection((Class<Object>) object).orElse(null);
        }
        return BeanIntrospector.SHARED.findIntrospection((Class<Object>) object.getClass()).orElse(null);
    }

    private <T> void validateParametersInternal(
            @NonNull Class<T> rootClass,
            @Nullable T object,
            @NonNull Object[] parameters,
            Argument[] arguments,
            int argLen,
            DefaultConstraintValidatorContext context,
            Set overallViolations,
            Path.Node parentNode) {
        for (int i = 0; i < argLen; i++) {
            Argument argument = arguments[i];
            final Class<?> parameterType = argument.getType();

            final AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();

            final boolean hasValid = annotationMetadata.hasStereotype(Validator.ANN_VALID);
            final boolean hasConstraint = annotationMetadata.hasStereotype(Validator.ANN_CONSTRAINT);


            if (!hasValid && !hasConstraint) {
                continue;
            }

            Object parameterValue = parameters[i];

            ValueExtractor<Object> valueExtractor = null;
            final boolean hasValue = parameterValue != null;
            final boolean isValid = hasValue && hasValid;
            final boolean isPublisher = hasValue && Publishers.isConvertibleToPublisher(parameterType);
            if (isPublisher) {
                instrumentPublisherArgumentWithValidation(
                        rootClass,
                        object,
                        parameters,
                        context,
                        i,
                        argument,
                        parameterType,
                        annotationMetadata,
                        parameterValue,
                        isValid
                );
            } else {
                final boolean isCompletionStage = hasValue && CompletionStage.class.isAssignableFrom(parameterType);
                if (isCompletionStage) {
                    instrumentCompletionStageArgumentWithValidation(
                            rootClass,
                            object,
                            parameters,
                            context,
                            i,
                            argument,
                            annotationMetadata,
                            parameterValue,
                            isValid
                    );
                } else {
                    if (hasValue) {
                        //noinspection unchecked
                        valueExtractor = (ValueExtractor<Object>) valueExtractorRegistry.findUnwrapValueExtractor(parameterType).orElse(null);
                    }

                    int finalIndex = i;
                    if (valueExtractor != null) {
                        valueExtractor.extractValues(parameterValue, (SimpleValueReceiver) (nodeName, unwrappedValue) -> validateParameterInternal(
                                rootClass,
                                object,
                                parameters,
                                context,
                                overallViolations,
                                argument.getName(),
                                unwrappedValue.getClass(),
                                finalIndex,
                                annotationMetadata,
                                unwrappedValue
                        ));
                    } else {
                        validateParameterInternal(
                                rootClass,
                                object,
                                parameters,
                                context,
                                overallViolations,
                                argument.getName(),
                                parameterType,
                                finalIndex,
                                annotationMetadata,
                                parameterValue
                        );
                    }


                    if (isValid) {
                        if (context.validatedObjects.contains(parameterValue)) {
                            // already validated
                            continue;
                        }
                        // cascade to bean
                        //noinspection unchecked
                        valueExtractor = (ValueExtractor<Object>) valueExtractorRegistry.findValueExtractor(parameterType).orElse(null);
                        if (valueExtractor != null) {
                            valueExtractor.extractValues(parameterValue, new ValueExtractor.ValueReceiver() {
                                @Override
                                public void value(String nodeName, Object object1) {
                                }

                                @Override
                                public void iterableValue(String nodeName, Object iterableValue) {
                                    if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                                        return;
                                    }
                                    cascadeToIterableValue(
                                            context,
                                            rootClass,
                                            object,
                                            parameterValue,
                                            parentNode,
                                            argument,
                                            iterableValue,
                                            overallViolations,
                                            null,
                                            null,
                                            true);
                                }

                                @Override
                                public void indexedValue(String nodeName, int i, Object iterableValue) {
                                    if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                                        return;
                                    }
                                    cascadeToIterableValue(
                                            context,
                                            rootClass,
                                            object,
                                            parameterValue,
                                            parentNode,
                                            argument,
                                            iterableValue,
                                            overallViolations,
                                            i,
                                            null,
                                            true);
                                }

                                @Override
                                public void keyedValue(String nodeName, Object key, Object keyedValue) {
                                    if (keyedValue != null && context.validatedObjects.contains(keyedValue)) {
                                        return;
                                    }
                                    cascadeToIterableValue(
                                            context,
                                            rootClass,
                                            object,
                                            parameterValue,
                                            parentNode,
                                            argument,
                                            keyedValue,
                                            overallViolations,
                                            null,
                                            key,
                                            false);
                                }
                            });
                        } else {
                            final BeanIntrospection<Object> beanIntrospection = getBeanIntrospection(parameterValue, parameterType);
                            if (beanIntrospection != null) {
                                try {
                                    context.addParameterNode(argument.getName(), i);
                                    cascadeToOneIntrospection(
                                            context,
                                            object,
                                            parameterValue,
                                            beanIntrospection,
                                            overallViolations
                                    );
                                } finally {
                                    context.removeLast();
                                }
                            } else {
                                context.addParameterNode(argument.getName(), i);
                                String messageTemplate = "{" + Introspected.class.getName() + ".message}";
                                overallViolations.add(new DefaultConstraintViolation(
                                        object,
                                        rootClass,
                                        null,
                                        parameterValue,
                                        messageSource.interpolate(messageTemplate, MessageSource.MessageContext.of(Collections.singletonMap("type", parameterType.getName()))),
                                        messageTemplate,
                                        new PathImpl(context.currentPath),
                                        null,
                                        parameters));
                                context.removeLast();
                            }
                        }
                    }
                }


            }

        }
    }

    private <T> void instrumentPublisherArgumentWithValidation(
            @NonNull Class<T> rootClass,
            @Nullable T object,
            @NonNull Object[] argumentValues,
            DefaultConstraintValidatorContext context,
            int argumentIndex,
            Argument argument,
            Class<?> parameterType,
            AnnotationMetadata annotationMetadata,
            Object parameterValue,
            boolean isValid) {
        final Flowable<Object> publisher = Publishers.convertPublisher(parameterValue, Flowable.class);
        PathImpl copied = new PathImpl(context.currentPath);
        final Flowable<Object> finalFlowable = publisher.flatMap(o -> {
            DefaultConstraintValidatorContext newContext =
                    new DefaultConstraintValidatorContext(
                            object,
                            copied
                    );
            Set newViolations = new HashSet();
            final BeanIntrospection<Object> beanIntrospection = !isValid || o == null || ClassUtils.isJavaBasicType(o.getClass()) ? null : getBeanIntrospection(o);
            if (beanIntrospection != null) {
                try {
                    context.addParameterNode(argument.getName(), argumentIndex);
                    cascadeToOneIntrospection(
                            newContext,
                            object,
                            o,
                            beanIntrospection,
                            newViolations
                    );
                } finally {
                    context.removeLast();
                }
            } else {

                final Class t = argument.getFirstTypeVariable().map(Argument::getType).orElse(null);
                validateParameterInternal(
                        rootClass,
                        object,
                        argumentValues,
                        newContext,
                        newViolations,
                        argument.getName(),
                        t != null ? t : Object.class,
                        argumentIndex,
                        annotationMetadata,
                        o
                );
            }

            if (!newViolations.isEmpty()) {
                return Flowable.error(
                        new ConstraintViolationException(newViolations)
                );
            }

            return Flowable.just(o);
        });
        argumentValues[argumentIndex] = Publishers.convertPublisher(finalFlowable, parameterType);
    }

    private <T> void instrumentCompletionStageArgumentWithValidation(
            @NonNull Class<T> rootClass,
            @Nullable T object,
            @NonNull Object[] argumentValues,
            DefaultConstraintValidatorContext context,
            int argumentIndex,
            Argument argument,
            AnnotationMetadata annotationMetadata,
            Object parameterValue,
            boolean isValid) {
        final CompletionStage<Object> publisher = (CompletionStage<Object>) parameterValue;
        PathImpl copied = new PathImpl(context.currentPath);
        final CompletionStage<Object> validatedStage = publisher.thenApply(o -> {
            DefaultConstraintValidatorContext newContext =
                    new DefaultConstraintValidatorContext(
                            object,
                            copied
                    );
            Set newViolations = new HashSet();
            final BeanIntrospection<Object> beanIntrospection = !isValid || o == null || ClassUtils.isJavaBasicType(o.getClass()) ? null : getBeanIntrospection(o);
            if (beanIntrospection != null) {
                try {
                    context.addParameterNode(argument.getName(), argumentIndex);
                    cascadeToOneIntrospection(
                            newContext,
                            object,
                            o,
                            beanIntrospection,
                            newViolations
                    );
                } finally {
                    context.removeLast();
                }
            } else {

                final Class t = argument.getFirstTypeVariable().map(Argument::getType).orElse(null);
                validateParameterInternal(
                        rootClass,
                        object,
                        argumentValues,
                        newContext,
                        newViolations,
                        argument.getName(),
                        t != null ? t : Object.class,
                        argumentIndex,
                        annotationMetadata,
                        o
                );
            }

            if (!newViolations.isEmpty()) {
                throw new ConstraintViolationException(newViolations);
            }

            return o;
        });
        argumentValues[argumentIndex] = validatedStage;
    }

    @SuppressWarnings("unchecked")
    private <T> void validateParameterInternal(
            @NonNull Class<T> rootClass,
            @Nullable T object,
            @NonNull Object[] argumentValues,
            @NonNull DefaultConstraintValidatorContext context,
            @NonNull Set overallViolations,
            @NonNull String parameterName,
            @NonNull Class<?> parameterType,
            int parameterIndex,
            @NonNull AnnotationMetadata annotationMetadata,
            @Nullable Object parameterValue) {
        try {
            context.addParameterNode(parameterName, parameterIndex);
            final List<Class<? extends Annotation>> constraintTypes =
                    annotationMetadata.getAnnotationTypesByStereotype(Constraint.class);

            // Constraints applied to the parameter
            for (Class<? extends Annotation> constraintType : constraintTypes) {
                final ConstraintValidator constraintValidator = constraintValidatorRegistry
                        .findConstraintValidator(constraintType, parameterType).orElse(null);
                if (constraintValidator != null) {
                    final AnnotationValue<? extends Annotation> annotationValue =
                            annotationMetadata.getAnnotation(constraintType);
                    if (annotationValue != null && !constraintValidator.isValid(parameterValue, annotationValue, context)) {
                        final String messageTemplate = buildMessageTemplate(annotationValue, annotationMetadata);
                        final Map<String, Object> variables = newConstraintVariables(annotationValue, parameterValue, annotationMetadata);
                        overallViolations.add(new DefaultConstraintViolation(
                                object,
                                rootClass,
                                null,
                                parameterValue,
                                messageSource.interpolate(messageTemplate, MessageSource.MessageContext.of(variables)),
                                messageTemplate,
                                new PathImpl(context.currentPath),
                                new DefaultConstraintDescriptor(annotationMetadata, constraintType, annotationValue),
                                argumentValues));
                    }
                }
            }

            // Constraints applied to the class used as a parameter
            final BeanIntrospection<Object> introspection = getBeanIntrospection(parameterType);
            if (introspection != null) {
                final List<Class<? extends Annotation>> pojoConstraints = introspection.getAnnotationTypesByStereotype(Constraint.class);

                for (Class<? extends Annotation> pojoConstraint : pojoConstraints) {
                    validatePojoInternal(rootClass, object, argumentValues, context, overallViolations, parameterType, parameterValue, pojoConstraint, introspection.getAnnotation(pojoConstraint));
                }
            }
        } finally {
            context.removeLast();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void validatePojoInternal(@NonNull Class<T> rootClass,
                                          @Nullable T object,
                                          @Nullable Object[] argumentValues,
                                          @NonNull DefaultConstraintValidatorContext context,
                                          @NonNull Set overallViolations,
                                          @NonNull Class<?> parameterType,
                                          @NonNull Object parameterValue,
                                          Class<? extends Annotation> pojoConstraint,
                                          AnnotationValue constraintAnnotation) {
        final ConstraintValidator constraintValidator = constraintValidatorRegistry
                .findConstraintValidator(pojoConstraint, parameterType).orElse(null);

        if (constraintValidator != null) {
            if (!constraintValidator.isValid(parameterValue, constraintAnnotation, context)) {
                final String propertyValue = "";
                BeanIntrospection<Object> beanIntrospection = getBeanIntrospection(parameterValue);
                if (beanIntrospection == null) {
                    throw new ValidationException("Passed object [" + parameterValue + "] cannot be introspected. Please annotate with @Introspected");
                }
                AnnotationMetadata beanAnnotationMetadata = beanIntrospection.getAnnotationMetadata();
                AnnotationValue<? extends Annotation> annotationValue = beanAnnotationMetadata.getAnnotation(pojoConstraint);

                final String messageTemplate = buildMessageTemplate(annotationValue, beanAnnotationMetadata);
                final Map<String, Object> variables = newConstraintVariables(annotationValue, propertyValue, beanAnnotationMetadata);
                overallViolations.add(new DefaultConstraintViolation(
                        object,
                        rootClass,
                        null,
                        parameterValue,
                        messageSource.interpolate(messageTemplate, MessageSource.MessageContext.of(variables)),
                        messageTemplate,
                        new PathImpl(context.currentPath),
                        new DefaultConstraintDescriptor(beanAnnotationMetadata, pojoConstraint, annotationValue),
                        argumentValues));
            }
        }
    }

    private <T> Set<ConstraintViolation<T>> doValidate(
            BeanIntrospection<T> introspection,
            @NonNull T rootBean,
            @NonNull Object object,
            Collection<? extends BeanProperty<Object, Object>> constrainedProperties,
            Collection<BeanProperty<Object, Object>> cascadeProperties,
            DefaultConstraintValidatorContext context,
            Set overallViolations,
            List<Class<? extends Annotation>> pojoConstraints) {
        @SuppressWarnings("unchecked")
        final Class<T> rootBeanClass = (Class<T>) rootBean.getClass();
        for (BeanProperty<Object, Object> constrainedProperty : constrainedProperties) {
            final Object propertyValue = constrainedProperty.get(object);
            //noinspection unchecked
            validateConstrainedPropertyInternal(
                    rootBeanClass,
                    rootBean,
                    object,
                    constrainedProperty,
                    constrainedProperty.getType(),
                    propertyValue,
                    context,
                    overallViolations,
                    null);
        }

        for (Class<? extends Annotation> pojoConstraint : pojoConstraints) {
            validatePojoInternal(
                    rootBeanClass,
                    rootBean,
                    null,
                    context,
                    overallViolations,
                    rootBeanClass,
                    object,
                    pojoConstraint,
                    introspection.getAnnotation(pojoConstraint));
        }

        // now handle cascading validation
        for (BeanProperty<Object, Object> cascadeProperty : cascadeProperties) {
            final Object propertyValue = cascadeProperty.get(object);
            if (propertyValue != null) {
                @SuppressWarnings("unchecked")
                final Optional<? extends ValueExtractor<Object>> opt = valueExtractorRegistry
                        .findValueExtractor((Class<Object>) propertyValue.getClass());

                opt.ifPresent(valueExtractor -> valueExtractor.extractValues(propertyValue, new ValueExtractor.ValueReceiver() {
                    @Override
                    public void value(String nodeName, Object object1) {

                    }

                    @Override
                    public void iterableValue(String nodeName, Object iterableValue) {
                        if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                            return;
                        }
                        cascadeToIterableValue(
                                context,
                                rootBeanClass,
                                rootBean,
                                object,
                                cascadeProperty,
                                iterableValue,
                                overallViolations,
                                null,
                                null,
                                true);
                    }

                    @Override
                    public void indexedValue(String nodeName, int i, Object iterableValue) {
                        if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                            return;
                        }
                        cascadeToIterableValue(
                                context,
                                rootBeanClass,
                                rootBean,
                                object,
                                cascadeProperty,
                                iterableValue,
                                overallViolations,
                                i,
                                null,
                                true);
                    }

                    @Override
                    public void keyedValue(String nodeName, Object key, Object keyedValue) {
                        if (keyedValue != null && context.validatedObjects.contains(keyedValue)) {
                            return;
                        }
                        cascadeToIterableValue(
                                context,
                                rootBeanClass,
                                rootBean,
                                object,
                                cascadeProperty,
                                keyedValue,
                                overallViolations,
                                null,
                                key,
                                false
                        );
                    }
                }));

                if (!opt.isPresent() && !context.validatedObjects.contains(propertyValue)) {
                    // maybe a bean
                    final Path.Node node = context.addPropertyNode(cascadeProperty.getName(), null);

                    try {
                        final boolean canCascade = canCascade(rootBeanClass, context, propertyValue, node);
                        if (canCascade) {
                            cascadeToOne(
                                    rootBeanClass,
                                    rootBean,
                                    object,
                                    context,
                                    overallViolations,
                                    cascadeProperty,
                                    cascadeProperty.getType(),
                                    propertyValue,
                                    null);
                        }
                    } finally {
                        context.removeLast();
                    }
                }
            }
        }
        //noinspection unchecked
        return Collections.unmodifiableSet(overallViolations);
    }

    private <T> boolean canCascade(
            Class<T> rootBeanClass,
            DefaultConstraintValidatorContext context,
            Object propertyValue,
            Path.Node node) {
        final boolean canCascade = traversableResolver.isCascadable(
                propertyValue,
                node,
                rootBeanClass,
                context.currentPath,
                ElementType.FIELD
        );
        final boolean isReachable = traversableResolver.isReachable(
                propertyValue,
                node,
                rootBeanClass,
                context.currentPath,
                ElementType.FIELD
        );
        return canCascade && isReachable;
    }

    private <T> void cascadeToIterableValue(
            DefaultConstraintValidatorContext context,
            @NonNull Class<T> rootClass,
            @Nullable T rootBean,
            Object object,
            BeanProperty<Object, Object> cascadeProperty,
            Object iterableValue,
            Set overallViolations,
            Integer index,
            Object key,
            boolean isIterable) {
        final DefaultPropertyNode container = new DefaultPropertyNode(
                cascadeProperty.getName(),
                cascadeProperty.getType(),
                index,
                key,
                ElementKind.CONTAINER_ELEMENT,
                isIterable
        );
        cascadeToOne(
                rootClass,
                rootBean,
                object,
                context,
                overallViolations,
                cascadeProperty,
                cascadeProperty.getType(),
                iterableValue,
                container
        );
    }

    private <T> void cascadeToIterableValue(
            DefaultConstraintValidatorContext context,
            @NonNull Class<T> rootClass,
            @Nullable T rootBean,
            @Nullable Object object,
            Path.Node node,
            Argument methodArgument,
            Object iterableValue,
            Set overallViolations,
            Integer index,
            Object key,
            boolean isIterable) {
        if (canCascade(rootClass, context, iterableValue, node)) {
            DefaultPropertyNode currentContainerNode = new DefaultPropertyNode(
                    methodArgument.getName(),
                    methodArgument.getClass(),
                    index,
                    key,
                    ElementKind.CONTAINER_ELEMENT,
                    isIterable
            );

            cascadeToOne(
                    rootClass,
                    rootBean,
                    object,
                    context,
                    overallViolations,
                    methodArgument,
                    methodArgument.getType(),
                    iterableValue,

                    currentContainerNode);
        }
    }

    private <T> void cascadeToOne(
            @NonNull Class<T> rootClass,
            @Nullable T rootBean,
            Object object,
            DefaultConstraintValidatorContext context,
            Set overallViolations,
            AnnotatedElement cascadeProperty,
            Class propertyType,
            Object propertyValue,
            @Nullable DefaultPropertyNode container) {

        final BeanIntrospection<Object> beanIntrospection = getBeanIntrospection(propertyValue);

        if (beanIntrospection != null) {
            if (container != null) {
                context.addPropertyNode(container.getName(), container);
            }
            try {
                cascadeToOneIntrospection(
                        context,
                        rootBean,
                        propertyValue,
                        beanIntrospection,
                        overallViolations);
            } finally {
                if (container != null) {
                    context.removeLast();
                }

            }

        } else {
            // try apply cascade rules to actual property
            //noinspection unchecked
            validateConstrainedPropertyInternal(
                    rootClass,
                    rootBean,
                    object,
                    cascadeProperty,
                    propertyType,
                    propertyValue,
                    context,
                    overallViolations,
                    container
            );
        }
    }

    private <T> void cascadeToOneIntrospection(DefaultConstraintValidatorContext context, T rootBean, Object bean, BeanIntrospection<Object> beanIntrospection, Set overallViolations) {
        context.validatedObjects.add(bean);
        final Collection<BeanProperty<Object, Object>> cascadeConstraints =
                beanIntrospection.getIndexedProperties(Constraint.class);
        final Collection<BeanProperty<Object, Object>> cascadeNestedProperties =
                beanIntrospection.getIndexedProperties(Valid.class);

        if (CollectionUtils.isNotEmpty(cascadeConstraints) || CollectionUtils.isNotEmpty(cascadeNestedProperties)) {
            doValidate(
                    beanIntrospection,
                    rootBean,
                    bean,
                    cascadeConstraints,
                    cascadeNestedProperties,
                    context,
                    overallViolations,
                    Collections.emptyList()
            );
        }
    }

    private <T> void validateConstrainedPropertyInternal(
            @NonNull Class<T> rootBeanClass,
            @Nullable T rootBean,
            @NonNull Object object,
            @NonNull AnnotatedElement constrainedProperty,
            @NonNull Class propertyType,
            @Nullable Object propertyValue,
            DefaultConstraintValidatorContext context,
            Set<ConstraintViolation<Object>> overallViolations,
            @Nullable DefaultPropertyNode container) {
        context.addPropertyNode(
                constrainedProperty.getName(), container
        );

        validatePropertyInternal(
                rootBeanClass,
                rootBean,
                object,
                context,
                overallViolations,
                propertyType,
                constrainedProperty,
                propertyValue
        );
        context.removeLast();
    }

    private <T> void validatePropertyInternal(
            @Nullable Class<T> rootBeanClass,
            @Nullable T rootBean,
            @Nullable Object object,
            @NonNull DefaultConstraintValidatorContext context,
            @NonNull Set<ConstraintViolation<Object>> overallViolations,
            @NonNull Class propertyType,
            @NonNull AnnotatedElement constrainedProperty,
            @Nullable Object propertyValue) {
        final AnnotationMetadata annotationMetadata = constrainedProperty.getAnnotationMetadata();
        final List<Class<? extends Annotation>> constraintTypes = annotationMetadata.getAnnotationTypesByStereotype(Constraint.class);
        for (Class<? extends Annotation> constraintType : constraintTypes) {

            ValueExtractor<Object> valueExtractor = null;
            if (propertyValue != null && !annotationMetadata.hasAnnotation(Valid.class)) {
                //noinspection unchecked
                valueExtractor = valueExtractorRegistry.findUnwrapValueExtractor((Class<Object>) propertyValue.getClass())
                        .orElse(null);
            }

            if (valueExtractor != null) {
                valueExtractor.extractValues(propertyValue, (SimpleValueReceiver) (nodeName, extractedValue) -> valueConstraintOnProperty(
                        rootBeanClass,
                        rootBean,
                        object,
                        context,
                        overallViolations,
                        constrainedProperty,
                        propertyType,
                        extractedValue,
                        constraintType
                ));
            } else {
                valueConstraintOnProperty(
                        rootBeanClass,
                        rootBean,
                        object,
                        context,
                        overallViolations,
                        constrainedProperty,
                        propertyType,
                        propertyValue,
                        constraintType
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void valueConstraintOnProperty(
            @Nullable Class<T> rootBeanClass,
            @Nullable T rootBean,
            @Nullable Object object,
            DefaultConstraintValidatorContext context,
            Set<ConstraintViolation<Object>> overallViolations,
            AnnotatedElement constrainedProperty,
            Class propertyType,
            @Nullable Object propertyValue,
            Class<? extends Annotation> constraintType) {
        final AnnotationMetadata annotationMetadata = constrainedProperty
                .getAnnotationMetadata();
        final List<? extends AnnotationValue<? extends Annotation>> annotationValues = annotationMetadata
                .getAnnotationValuesByType(constraintType);

        Set<AnnotationValue<? extends Annotation>> constraints = new HashSet<>(3);
        for (Class<?> group : context.groups) {
            for (AnnotationValue<? extends Annotation> annotationValue : annotationValues) {
                final Class<?>[] classValues = annotationValue.classValues("groups");
                if (ArrayUtils.isEmpty(classValues)) {
                    if (context.groups == DEFAULT_GROUPS || group == Default.class) {
                        constraints.add(annotationValue);
                    }
                } else {
                    final List<Class> constraintGroups = Arrays.asList(classValues);
                    if (constraintGroups.contains(group)) {
                        constraints.add(annotationValue);
                    }
                }
            }
        }

        @SuppressWarnings("unchecked") final Class<Object> targetType = propertyValue != null ? (Class<Object>) propertyValue.getClass() : propertyType;
        final ConstraintValidator<? extends Annotation, Object> validator = constraintValidatorRegistry
                .findConstraintValidator(constraintType, targetType).orElse(null);
        if (validator != null) {
            for (AnnotationValue annotationValue : constraints) {
                //noinspection unchecked
                if (!validator.isValid(propertyValue, annotationValue, context)) {

                    final String messageTemplate = buildMessageTemplate(annotationValue, annotationMetadata);
                    Map<String, Object> variables = newConstraintVariables(annotationValue, propertyValue, annotationMetadata);
                    //noinspection unchecked
                    overallViolations.add(
                            new DefaultConstraintViolation(
                                    rootBean,
                                    rootBeanClass,
                                    object,
                                    propertyValue,
                                    messageSource.interpolate(messageTemplate, MessageSource.MessageContext.of(variables)),
                                    messageTemplate,
                                    new PathImpl(context.currentPath),
                                    new DefaultConstraintDescriptor(annotationMetadata, constraintType, annotationValue))

                    );
                }
            }
        }
    }

    private Map<String, Object> newConstraintVariables(AnnotationValue annotationValue, @Nullable Object propertyValue, AnnotationMetadata annotationMetadata) {
        final Map<?, ?> values = annotationValue.getValues();
        int initSize = (int) Math.ceil(values.size() / 0.75);
        Map<String, Object> variables = new LinkedHashMap<>(initSize);
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            variables.put(entry.getKey().toString(),  entry.getValue());
        }
        variables.put("validatedValue", propertyValue);
        final Map<String, Object> defaultValues = annotationMetadata.getDefaultValues(annotationValue.getAnnotationName());
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            final String n = entry.getKey();
            if (!variables.containsKey(n)) {
                final Object v = entry.getValue();
                if (v != null) {
                    variables.put(n, v);
                }
            }
        }
        return variables;
    }

    private String buildMessageTemplate(AnnotationValue<?> annotationValue, AnnotationMetadata annotationMetadata) {
        return annotationValue.stringValue("message")
                .orElseGet(() ->
                        annotationMetadata.getDefaultValue(annotationValue.getAnnotationName(), "message", String.class)
                            .orElse("{" + annotationValue.getAnnotationName() + ".message}")
                        );
    }

    @NonNull
    @Override
    public <T> Publisher<T> validatePublisher(@NonNull Publisher<T> publisher, Class<?>... groups) {
        ArgumentUtils.requireNonNull("publisher", publisher);
        final Flowable<T> flowable = Publishers.convertPublisher(publisher, Flowable.class);
        return flowable.flatMap(object -> {
            final Set<ConstraintViolation<Object>> constraintViolations = validate(object, groups);
            if (!constraintViolations.isEmpty()) {
                return Flowable.error(new ConstraintViolationException(constraintViolations));
            }
            return Flowable.just(object);
        });
    }

    @NonNull
    @Override
    public <T> CompletionStage<T> validateCompletionStage(@NonNull CompletionStage<T> completionStage, Class<?>... groups) {
        ArgumentUtils.requireNonNull("completionStage", completionStage);
        return completionStage.thenApply(t -> {
            final Set<ConstraintViolation<Object>> constraintViolations = validate(t, groups);
            if (!constraintViolations.isEmpty()) {
                throw new ConstraintViolationException(constraintViolations);
            }
            return t;
        });
    }

    @Override
    public <T> void validateBeanArgument(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull InjectionPoint injectionPoint,
            @NonNull Argument<T> argument,
            int index,
            @Nullable T value) throws BeanInstantiationException {


        final AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        final boolean hasValid = annotationMetadata.hasStereotype(Valid.class);
        final boolean hasConstraint = annotationMetadata.hasStereotype(Constraint.class);
        final Class<T> parameterType = argument.getType();
        final Class rootClass = injectionPoint.getDeclaringBean().getBeanType();
        DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(value);
        Set overallViolations = new HashSet<>(5);
        if (hasConstraint) {
            final Path.Node parentNode = context.addConstructorNode(rootClass.getName(), injectionPoint.getDeclaringBean().getConstructor().getArguments());
            ValueExtractor<Object> valueExtractor = (ValueExtractor<Object>) valueExtractorRegistry.findValueExtractor(parameterType).orElse(null);
            if (valueExtractor != null) {
                valueExtractor.extractValues(value, new ValueExtractor.ValueReceiver() {
                    @Override
                    public void value(String nodeName, Object object1) {
                    }

                    @Override
                    public void iterableValue(String nodeName, Object iterableValue) {
                        if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                            return;
                        }
                        cascadeToIterableValue(
                                context,
                                rootClass,
                                null,
                                value,
                                parentNode,
                                argument,
                                iterableValue,
                                overallViolations,
                                null,
                                null,
                                true);
                    }

                    @Override
                    public void indexedValue(String nodeName, int i, Object iterableValue) {
                        if (iterableValue != null && context.validatedObjects.contains(iterableValue)) {
                            return;
                        }
                        cascadeToIterableValue(
                                context,
                                rootClass,
                                null,
                                value,
                                parentNode,
                                argument,
                                iterableValue,
                                overallViolations,
                                i,
                                null,
                                true);
                    }

                    @Override
                    public void keyedValue(String nodeName, Object key, Object keyedValue) {
                        if (keyedValue != null && context.validatedObjects.contains(keyedValue)) {
                            return;
                        }
                        cascadeToIterableValue(
                                context,
                                rootClass,
                                null,
                                value,
                                parentNode,
                                argument,
                                keyedValue,
                                overallViolations,
                                null,
                                key,
                                false);
                    }
                });
            } else {
                validateParameterInternal(
                        rootClass,
                        null,
                        ArrayUtils.EMPTY_OBJECT_ARRAY,
                        context,
                        overallViolations,
                        argument.getName(),
                        parameterType,
                        index,
                        annotationMetadata,
                        value
                );
            }
            context.removeLast();
        } else if (hasValid && value != null) {
            final BeanIntrospection<Object> beanIntrospection = getBeanIntrospection(value, parameterType);
            if (beanIntrospection != null) {
                try {
                    context.addParameterNode(argument.getName(), index);
                    cascadeToOneIntrospection(
                            context,
                            null,
                            value,
                            beanIntrospection,
                            overallViolations
                    );
                } finally {
                    context.removeLast();
                }
            }
        }

        failOnError(resolutionContext, overallViolations, rootClass);
    }

    @Override
    public <T> void validateBean(@NonNull BeanResolutionContext resolutionContext, @NonNull BeanDefinition<T> definition, @NonNull T bean) throws BeanInstantiationException {
        final BeanIntrospection<T> introspection = (BeanIntrospection<T>) getBeanIntrospection(bean);
        if (introspection != null) {
            Set<ConstraintViolation<T>> errors = validate(introspection, bean);
            final Class<?> beanType = bean.getClass();
            failOnError(resolutionContext, errors, beanType);
        } else if (bean instanceof Intercepted && definition.hasStereotype(ConfigurationReader.class)) {
            final Collection<ExecutableMethod<T, ?>> executableMethods = definition.getExecutableMethods();
            if (CollectionUtils.isNotEmpty(executableMethods)) {
                Set<ConstraintViolation<Object>> errors = new HashSet<>();
                final DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(bean);
                final Class<T> beanType = definition.getBeanType();
                final Class<?>[] interfaces = beanType.getInterfaces();
                if (ArrayUtils.isNotEmpty(interfaces)) {
                    context.addConstructorNode(interfaces[0].getSimpleName());
                } else {
                    context.addConstructorNode(beanType.getSimpleName());
                }
                for (ExecutableMethod executableMethod : executableMethods) {
                    if (executableMethod.hasAnnotation(Property.class)) {
                        final boolean hasConstraint = executableMethod.hasStereotype(Constraint.class);
                        final boolean isValid = executableMethod.hasStereotype(Valid.class);
                        if (hasConstraint || isValid) {
                            final Object value = executableMethod.invoke(bean);
                            validateConstrainedPropertyInternal(
                                    beanType,
                                    bean,
                                    bean,
                                    executableMethod,
                                    executableMethod.getReturnType().getType(),
                                    value,
                                    context,
                                    errors,
                                    null
                            );
                        }
                    }
                }

                failOnError(resolutionContext, errors, beanType);
            }
        }
    }

    private <T> void failOnError(@NonNull BeanResolutionContext resolutionContext, Set<ConstraintViolation<T>> errors, Class<?> beanType) {
        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Validation failed for bean definition [");
            builder.append(beanType.getName());
            builder.append("]\nList of constraint violations:[\n");
            for (ConstraintViolation<?> violation : errors) {
                builder.append("\t").append(violation.getPropertyPath()).append(" - ").append(violation.getMessage()).append("\n");
            }
            builder.append("]");
            throw new BeanInstantiationException(resolutionContext, builder.toString());
        }
    }

    /**
     * The context object.
     */
    private final class DefaultConstraintValidatorContext implements ConstraintValidatorContext {
        final Set<Object> validatedObjects = new HashSet<>(20);
        final PathImpl currentPath;
        final List<Class> groups;

        private <T> DefaultConstraintValidatorContext(T object, Class<?>... groups) {
            this(object, new PathImpl(), groups);
        }

        private <T> DefaultConstraintValidatorContext(T object, PathImpl path, Class<?>... groups) {
            if (object != null) {
                validatedObjects.add(object);
            }
            if (ArrayUtils.isNotEmpty(groups)) {
                this.groups = Arrays.asList(groups);
            } else {
                this.groups = DEFAULT_GROUPS;
            }

            this.currentPath = path != null ? path : new PathImpl();
        }

        private DefaultConstraintValidatorContext(Class<?>... groups) {
            this(null, groups);
        }

        @NonNull
        @Override
        public ClockProvider getClockProvider() {
            return clockProvider;
        }

        @Nullable
        @Override
        public Object getRootBean() {
            return validatedObjects.isEmpty() ? null : validatedObjects.iterator().next();
        }

        Path.Node addPropertyNode(String name, @Nullable DefaultPropertyNode container) {
            final DefaultPropertyNode node;
            if (container != null) {
                node = new DefaultPropertyNode(
                        name, container
                );
            } else {
                node = new DefaultPropertyNode(name, null, null, null, ElementKind.PROPERTY, false);
            }
            currentPath.nodes.add(node);
            return node;
        }

        Path.Node addReturnValueNode(String name) {
            final DefaultReturnValueNode returnValueNode;
            returnValueNode = new DefaultReturnValueNode(name);
            currentPath.nodes.add(returnValueNode);
            return returnValueNode;
        }

        void removeLast() {
            currentPath.nodes.removeLast();
        }

        Path.Node addMethodNode(MethodReference<?, ?> reference) {
            final DefaultMethodNode methodNode = new DefaultMethodNode(reference);
            currentPath.nodes.add(methodNode);
            return methodNode;
        }

        void addParameterNode(String name, int index) {
            final DefaultParameterNode node;
            node = new DefaultParameterNode(
                    name, index
            );
            currentPath.nodes.add(node);
        }

        Path.Node addConstructorNode(String simpleName, Argument<?>... constructorArguments) {
            final DefaultConstructorNode node = new DefaultConstructorNode(new MethodReference<Object, Object>() {

                @Override
                public Argument[] getArguments() {
                    return constructorArguments;
                }

                @Override
                public Method getTargetMethod() {
                    return null;
                }

                @Override
                public ReturnType<Object> getReturnType() {
                    return null;
                }

                @Override
                public Class getDeclaringType() {
                    return null;
                }

                @Override
                public String getMethodName() {
                    return simpleName;
                }
            });
            currentPath.nodes.add(node);
            return node;
        }
    }

    /**
     * Path implementation.
     */
    private final class PathImpl implements Path {

        final Deque<Node> nodes;

        /**
         * Copy constructor.
         *
         * @param nodes The nodes
         */
        private PathImpl(PathImpl nodes) {
            this.nodes = new LinkedList<>(nodes.nodes);
        }

        private PathImpl() {
            this.nodes = new LinkedList<>();
        }

        @Override
        public Iterator<Node> iterator() {
            return nodes.iterator();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            final Iterator<Node> i = nodes.iterator();
            while (i.hasNext()) {
                final Node node = i.next();
                builder.append(node.getName());
                if (node.getKind() == ElementKind.CONTAINER_ELEMENT) {
                    final Integer index = node.getIndex();
                    if (index != null) {
                        builder.append('[').append(index).append(']');
                    } else {
                        final Object key = node.getKey();
                        if (key != null) {
                            builder.append('[').append(key).append(']');
                        } else {
                            builder.append("[]");
                        }
                    }

                }

                if (i.hasNext()) {
                    builder.append('.');
                }

            }
            return builder.toString();
        }
    }

    /**
     * Constructor node.
     */
    private final class DefaultConstructorNode extends DefaultMethodNode implements Path.ConstructorNode {
        public DefaultConstructorNode(MethodReference<Object, Object> methodReference) {
            super(methodReference);
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.CONSTRUCTOR;
        }
    }

    /**
     * Method node implementation.
     */
    private class DefaultMethodNode implements Path.MethodNode {

        private final MethodReference<?, ?> methodReference;

        public DefaultMethodNode(MethodReference<?, ?> methodReference) {
            this.methodReference = methodReference;
        }

        @Override
        public List<Class<?>> getParameterTypes() {
            return Arrays.asList(methodReference.getArgumentTypes());
        }

        @Override
        public String getName() {
            return methodReference.getMethodName();
        }

        @Override
        public boolean isInIterable() {
            return false;
        }

        @Override
        public Integer getIndex() {
            return null;
        }

        @Override
        public Object getKey() {
            return null;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.METHOD;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }
    }

    /**
     * Method node implementation.
     */
    private final class DefaultParameterNode extends DefaultPropertyNode implements Path.ParameterNode {

        private final int parameterIndex;

        DefaultParameterNode(@NonNull String name, int parameterIndex) {
            super(name, null, null, null, ElementKind.PARAMETER, false);
            this.parameterIndex = parameterIndex;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.PARAMETER;
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }

        @Override
        public int getParameterIndex() {
            return parameterIndex;
        }
    }

    /**
     * Default Return value node implementation.
     */
    private class DefaultReturnValueNode implements Path.ReturnValueNode {
        private final String name;
        private final Integer index;
        private final Object key;
        private final ElementKind kind;
        private final boolean isInIterable;

        public DefaultReturnValueNode(String name,
                                      Integer index,
                                      Object key,
                                      ElementKind kind,
                                      boolean isInIterable) {
            this.name = name;
            this.index = index;
            this.key = key;
            this.kind = kind;
            this.isInIterable = isInIterable;
        }

        public DefaultReturnValueNode(String name) {
            this(name, null, null, ElementKind.RETURN_VALUE, false);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Integer getIndex() {
            return index;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public ElementKind getKind() {
            return kind;
        }

        @Override
        public boolean isInIterable() {
            return isInIterable;
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }
    }

    /**
     * Default property node impl.
     */
    private class DefaultPropertyNode implements Path.PropertyNode {
        private final Class<?> containerClass;
        private final String name;
        private final Integer index;
        private final Object key;
        private final ElementKind kind;
        private final boolean isIterable;

        DefaultPropertyNode(
                @NonNull String name,
                @Nullable Class<?> containerClass,
                @Nullable Integer index,
                @Nullable Object key,
                @NonNull ElementKind kind,
                boolean isIterable) {
            this.containerClass = containerClass;
            this.name = name;
            this.index = index;
            this.key = key;
            this.kind = kind;
            this.isIterable = isIterable || index != null;
        }

        DefaultPropertyNode(
                @NonNull String name,
                @NonNull DefaultPropertyNode parent
        ) {
            this(name, parent.containerClass, parent.getIndex(), parent.getKey(), ElementKind.CONTAINER_ELEMENT, parent.isInIterable());
        }

        @Override
        public Class<?> getContainerClass() {
            return containerClass;
        }

        @Override
        public Integer getTypeArgumentIndex() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInIterable() {
            return isIterable;
        }

        @Override
        public Integer getIndex() {
            return index;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public ElementKind getKind() {
            return kind;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }
    }

    /**
     * Default implementation of {@link ConstraintViolation}.
     *
     * @param <T> The bean type.
     */
    private final class DefaultConstraintViolation<T> implements ConstraintViolation<T> {

        private final T rootBean;
        private final Object invalidValue;
        private final String message;
        private final String messageTemplate;
        private final Path path;
        private final Class<T> rootBeanClass;
        private final Object leafBean;
        private final ConstraintDescriptor<?> constraintDescriptor;
        private final Object[] executableParams;

        private DefaultConstraintViolation(
                @Nullable T rootBean,
                @Nullable Class<T> rootBeanClass,
                Object leafBean,
                Object invalidValue,
                String message,
                String messageTemplate,
                Path path,
                ConstraintDescriptor<?> constraintDescriptor,
                Object... executableParams) {
            this.rootBean = rootBean;
            this.rootBeanClass = rootBeanClass;
            this.invalidValue = invalidValue;
            this.message = message;
            this.messageTemplate = messageTemplate;
            this.path = path;
            this.leafBean = leafBean;
            this.constraintDescriptor = constraintDescriptor;
            this.executableParams = executableParams;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public String getMessageTemplate() {
            return messageTemplate;
        }

        @Override
        public T getRootBean() {
            return rootBean;
        }

        @Override
        public Class<T> getRootBeanClass() {
            return rootBeanClass;
        }

        @Override
        public Object getLeafBean() {
            return leafBean;
        }

        @Override
        public Object[] getExecutableParameters() {
            if (executableParams != null) {
                return executableParams;
            } else {
                return ArrayUtils.EMPTY_OBJECT_ARRAY;
            }
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public Path getPropertyPath() {
            return path;
        }

        @Override
        public Object getInvalidValue() {
            return invalidValue;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return constraintDescriptor;
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }
    }

    /**
     * An empty descriptor with no constraints.
     */
    private final class EmptyDescriptor implements BeanDescriptor, ElementDescriptor.ConstraintFinder {
        private final Class<?> elementClass;

        EmptyDescriptor(Class<?> elementClass) {
            this.elementClass = elementClass;
        }

        @Override
        public boolean isBeanConstrained() {
            return false;
        }

        @Override
        public PropertyDescriptor getConstraintsForProperty(String propertyName) {
            return null;
        }

        @Override
        public Set<PropertyDescriptor> getConstrainedProperties() {
            return Collections.emptySet();
        }

        @Override
        public MethodDescriptor getConstraintsForMethod(String methodName, Class<?>... parameterTypes) {
            return null;
        }

        @Override
        public Set<MethodDescriptor> getConstrainedMethods(MethodType methodType, MethodType... methodTypes) {
            return Collections.emptySet();
        }

        @Override
        public ConstructorDescriptor getConstraintsForConstructor(Class<?>... parameterTypes) {
            return null;
        }

        @Override
        public Set<ConstructorDescriptor> getConstrainedConstructors() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasConstraints() {
            return false;
        }

        @Override
        public Class<?> getElementClass() {
            return elementClass;
        }

        @Override
        public ConstraintFinder unorderedAndMatchingGroups(Class<?>... groups) {
            return this;
        }

        @Override
        public ConstraintFinder lookingAt(Scope scope) {
            return this;
        }

        @Override
        public ConstraintFinder declaredOn(ElementType... types) {
            return this;
        }

        @Override
        public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
            return Collections.emptySet();
        }

        @Override
        public ConstraintFinder findConstraints() {
            return this;
        }
    }
}
