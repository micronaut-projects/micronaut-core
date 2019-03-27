/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;
import io.micronaut.validation.validator.constraints.DefaultConstraintValidators;
import io.micronaut.validation.validator.extractors.DefaultValueExtractors;
import io.micronaut.validation.validator.extractors.SimpleValueReceiver;
import io.micronaut.validation.validator.extractors.ValueExtractorRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.*;
import javax.validation.metadata.BeanDescriptor;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.valueextraction.ValueExtractor;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Default implementation of the {@link Validator} interface.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class DefaultValidator implements Validator, ExecutableMethodValidator {

    private final ConstraintValidatorRegistry constraintValidatorRegistry;
    private final ClockProvider clockProvider;
    private final ValueExtractorRegistry valueExtractorRegistry;
    private final TraversableResolver traversableResolver;
    private final ExecutionHandleLocator executionHandleLocator;

    /**
     * Default constructor.
     *
     * @param constraintValidatorRegistry The validator registry.
     * @param valueExtractorRegistry      The value extractor registry.
     * @param clockProvider               The clock provider
     * @param traversableResolver         The traversable resolver to use
     * @param executionHandleLocator      The execution handle locator for located executable methods to validate
     */
    @Inject
    protected DefaultValidator(
            @Nullable ConstraintValidatorRegistry constraintValidatorRegistry,
            @Nullable ValueExtractorRegistry valueExtractorRegistry,
            @Nullable ClockProvider clockProvider,
            @Nullable TraversableResolver traversableResolver,
            @Nullable ExecutionHandleLocator executionHandleLocator) {
        ArgumentUtils.requireNonNull("constraintValidatorRegistry", constraintValidatorRegistry);
        ArgumentUtils.requireNonNull("valueExtractorRegistry", valueExtractorRegistry);
        ArgumentUtils.requireNonNull("clockProvider", clockProvider);
        this.constraintValidatorRegistry = constraintValidatorRegistry == null ? new DefaultConstraintValidators() : constraintValidatorRegistry;
        this.clockProvider = clockProvider == null ? new DefaultClockProvider() : clockProvider;
        this.valueExtractorRegistry = valueExtractorRegistry == null ? new DefaultValueExtractors() : valueExtractorRegistry;
        this.traversableResolver = traversableResolver != null ? traversableResolver : new TraversableResolver() {
            @Override
            public boolean isReachable(Object object, Path.Node node, Class<?> rootType, Path path, ElementType elementType) {
                return true;
            }

            @Override
            public boolean isCascadable(Object object, Path.Node node, Class<?> rootType, Path path, ElementType elementType) {
                return true;
            }
        };
        this.executionHandleLocator = executionHandleLocator != null ? executionHandleLocator : new ExecutionHandleLocator() {
        };
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validate(@Nonnull T object, @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        final BeanIntrospection<Object> introspection = getBeanIntrospection(object);
        if (introspection == null) {
            throw new ValidationException("Passed object [" + object + "] cannot be introspected. Please annotation with @Introspected");
        }
        final Collection<? extends BeanProperty<Object, Object>> constrainedProperties = introspection.getIndexedProperties(Constraint.class);
        final Collection<BeanProperty<Object, Object>> cascadeProperties =
                introspection.getIndexedProperties(Valid.class);

        if (CollectionUtils.isNotEmpty(constrainedProperties) || CollectionUtils.isNotEmpty(cascadeProperties)) {
            DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object);
            Set<ConstraintViolation<T>> overallViolations = new HashSet<>(5);
            return doValidate(
                    object,
                    object,
                    constrainedProperties,
                    cascadeProperties,
                    context,
                    overallViolations
            );
        }
        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(
            @Nonnull T object,
            @Nonnull String propertyName,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        ArgumentUtils.requireNonNull("propertyName", propertyName);
        final BeanIntrospection<Object> introspection = getBeanIntrospection(object);
        if (introspection == null) {
            throw new ValidationException("Passed object [" + object + "] cannot be introspected. Please annotation with @Introspected");
        }

        final Optional<BeanProperty<Object, Object>> property = introspection.getProperty(propertyName);

        if (property.isPresent()) {
            final BeanProperty<Object, Object> constrainedProperty = property.get();
            DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object);
            Set overallViolations = new HashSet<>(5);
            final Object propertyValue = constrainedProperty.get(object);

            validateConstrainedPropertyInternal(
                    object,
                    object,
                    constrainedProperty,
                    constrainedProperty.getType(),
                    propertyValue,
                    context,
                    overallViolations
            );

            //noinspection unchecked
            return Collections.unmodifiableSet(overallViolations);
        }

        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(
            @Nonnull Class<T> beanType,
            @Nonnull String propertyName,
            @Nullable Object value,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("propertyName", propertyName);

        final BeanIntrospection<Object> introspection = getBeanIntrospection(beanType);
        if (introspection == null) {
            throw new ValidationException("Passed bean type [" + beanType + "] cannot be introspected. Please annotation with @Introspected");
        }

        final BeanProperty<Object, Object> beanProperty = introspection.getProperty(propertyName)
                .orElseThrow(() -> new ValidationException("No property [" + propertyName + "] found on type: " + beanType));


        final HashSet overallViolations = new HashSet<>(5);
        final DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext();
        try {
            context.addPropertyNode(propertyName);
            validatePropertyInternal(null, null, context, overallViolations, beanProperty.getType(), beanProperty, value);
        } finally {
            context.removeLast();
        }

        return Collections.unmodifiableSet(overallViolations);
    }

    @Override
    public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
        throw new UnsupportedOperationException("BeanDescriptor metadata not supported by this implementation");
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        throw new UnsupportedOperationException("Validator unwrapping not supported by this implementation");
    }

    @Override
    @Nonnull
    public ExecutableMethodValidator forExecutables() {
        return this;
    }


    /**
     * looks up a bean introspection for the given object.
     *
     * @param object The object, never null
     * @return The introspection or null
     */
    protected @Nullable
    BeanIntrospection<Object> getBeanIntrospection(@Nonnull Object object) {
        //noinspection ConstantConditions
        if (object == null) {
            return null;
        }
        if (object instanceof Class) {
            return BeanIntrospector.SHARED.findIntrospection((Class<Object>) object).orElse(null);
        }
        return BeanIntrospector.SHARED.findIntrospection((Class<Object>) object.getClass()).orElse(null);
    }

    private <T> Set<ConstraintViolation<T>> doValidate(
            @Nonnull T rootBean,
            @Nonnull Object object,
            Collection<? extends BeanProperty<Object, Object>> constrainedProperties,
            Collection<BeanProperty<Object, Object>> cascadeProperties,
            DefaultConstraintValidatorContext context,
            Set overallViolations) {
        for (BeanProperty<Object, Object> constrainedProperty : constrainedProperties) {
            final Object propertyValue = constrainedProperty.get(object);
            validateConstrainedPropertyInternal(
                    rootBean,
                    object,
                    constrainedProperty,
                    constrainedProperty.getType(),
                    propertyValue,
                    context,
                    overallViolations
            );
        }

        // now handle cascading validation
        for (BeanProperty<Object, Object> cascadeProperty : cascadeProperties) {
            final Object propertyValue = cascadeProperty.get(object);
            if (propertyValue != null) {
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
                    final Path.Node node = context.addPropertyNode(cascadeProperty.getName());

                    try {
                        final boolean canCascade = canCascade(rootBean, context, propertyValue, node);
                        if (canCascade) {
                            cascadeToOne(
                                    rootBean,
                                    object,
                                    context,
                                    overallViolations,
                                    cascadeProperty,
                                    cascadeProperty.getType(),
                                    propertyValue
                            );
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

    private <T> boolean canCascade(@Nonnull T rootBean, DefaultConstraintValidatorContext context, Object propertyValue, Path.Node node) {
        final boolean isCascadable = traversableResolver.isCascadable(propertyValue, node, rootBean.getClass(), context.currentPath, ElementType.FIELD);
        final boolean isReachable = traversableResolver.isReachable(propertyValue, node, rootBean.getClass(), context.currentPath, ElementType.FIELD);
        return isCascadable && isReachable;
    }

    private <T> void cascadeToIterableValue(
            DefaultConstraintValidatorContext context,
            @Nonnull T rootBean,
            Object object,
            BeanProperty<Object, Object> cascadeProperty,
            Object iterableValue,
            Set overallViolations,
            Integer index,
            Object key,
            boolean isIterable) {
        context.currentContainerNode = new DefaultPropertyNode(
                cascadeProperty.getName(),
                cascadeProperty.getType(),
                index,
                key,
                ElementKind.CONTAINER_ELEMENT,
                isIterable
        );
        try {
                cascadeToOne(
                        rootBean,
                        object,
                        context,
                        overallViolations,
                        cascadeProperty,
                        cascadeProperty.getType(),
                        iterableValue
                );
        } finally {

            context.removeLast();
        }
    }

    private <T> void cascadeToIterableValue(
            DefaultConstraintValidatorContext context,
            @Nonnull T rootBean,
            Object object,
            Path.Node node,
            Argument methodArgument,
            Object iterableValue,
            Set overallViolations,
            Integer index,
            Object key,
            boolean isIterable) {
        try {
            if (canCascade(rootBean, context, iterableValue, node)) {
                context.currentContainerNode = new DefaultPropertyNode(
                        methodArgument.getName(),
                        methodArgument.getClass(),
                        index,
                        key,
                        ElementKind.CONTAINER_ELEMENT,
                        isIterable
                );

                cascadeToOne(
                        rootBean,
                        object,
                        context,
                        overallViolations,
                        methodArgument,
                        methodArgument.getType(),
                        iterableValue

                );
            }
        } finally {
            context.currentContainerNode = null;
        }
    }

    private <T> void cascadeToOne(
            T rootBean,
            Object object,
            DefaultConstraintValidatorContext context,
            Set overallViolations,
            AnnotatedElement cascadeProperty,
            Class propertyType,
            Object propertyValue) {

        final BeanIntrospection<Object> beanIntrospection = getBeanIntrospection(propertyValue);

        if (beanIntrospection != null) {
            cascadeToOneIntrospection(context, rootBean, propertyValue, beanIntrospection, overallViolations);

        } else {
            // try apply cascade rules to actual property
            validateConstrainedPropertyInternal(
                    rootBean,
                    object,
                    cascadeProperty,
                    propertyType,
                    propertyValue,
                    context,
                    overallViolations
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
                    rootBean,
                    bean,
                    cascadeConstraints,
                    cascadeNestedProperties,
                    context,
                    overallViolations
            );
        }
    }

    private <T> void validateConstrainedPropertyInternal(
            @Nullable T rootBean,
            @Nonnull Object object,
            @Nonnull AnnotatedElement constrainedProperty,
            @Nonnull Class propertyType,
            @Nullable Object propertyValue,
            DefaultConstraintValidatorContext context,
            Set<ConstraintViolation<Object>> overallViolations) {
        context.addPropertyNode(
                constrainedProperty.getName()
        );

        validatePropertyInternal(
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
            @Nullable T rootBean,
            @Nullable Object object,
            @Nonnull DefaultConstraintValidatorContext context,
            @Nonnull Set<ConstraintViolation<Object>> overallViolations,
            @Nonnull Class propertyType,
            @Nonnull AnnotatedElement constrainedProperty,
            @Nullable Object propertyValue) {
        final AnnotationMetadata annotationMetadata = constrainedProperty.getAnnotationMetadata();
        final List<Class<? extends Annotation>> constraintTypes = annotationMetadata.getAnnotationTypesByStereotype(Constraint.class);
        for (Class<? extends Annotation> constraintType : constraintTypes) {

            ValueExtractor<Object> valueExtractor = null;
            if (propertyValue != null && !annotationMetadata.hasAnnotation(Valid.class)) {
                valueExtractor = valueExtractorRegistry.findUnwrapValueExtractor((Class<Object>) propertyValue.getClass())
                        .orElse(null);
            }

            if (valueExtractor != null) {
                valueExtractor.extractValues(propertyValue, (SimpleValueReceiver) (nodeName, extractedValue) -> valueConstraintOnProperty(
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

    private <T> void valueConstraintOnProperty(
            @Nullable T rootBean,
            @Nullable Object object,
            DefaultConstraintValidatorContext context,
            Set<ConstraintViolation<Object>> overallViolations,
            AnnotatedElement constrainedProperty,
            Class propertyType,
            @Nullable Object propertyValue,
            Class<? extends Annotation> constraintType) {
        final List<? extends AnnotationValue<? extends Annotation>> annotationValues = constrainedProperty
                .getAnnotationMetadata()
                .getAnnotationValuesByType(constraintType);

        @SuppressWarnings("unchecked") final Class<Object> targetType = propertyValue != null ? (Class<Object>) propertyValue.getClass() : propertyType;
        final ConstraintValidator<? extends Annotation, Object> validator = constraintValidatorRegistry
                .findConstraintValidator(constraintType, targetType).orElse(null);
        if (validator != null) {
            for (AnnotationValue annotationValue : annotationValues) {
                //noinspection unchecked
                if (!validator.isValid(propertyValue, annotationValue, context)) {

                    final String messageTemplate = buildMessageTemplate(annotationValue);
                    //noinspection unchecked
                    overallViolations.add(
                            new DefaultConstraintViolation(
                                    rootBean,
                                    rootBean != null ? rootBean.getClass() : null,
                                    object,
                                    propertyValue,
                                    messageTemplate, // TODO: message interpolation
                                    messageTemplate,
                                    new PathImpl(context.currentPath)
                            )
                    );
                }
            }
        }
    }

    private String buildMessageTemplate(AnnotationValue annotationValue) {
        return (String) annotationValue.get("message", String.class)
                .orElse("{" + annotationValue.getAnnotationName() + ".message}");
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateParameters(
            @Nonnull T object,
            @Nonnull ExecutableMethod method,
            @Nullable Object[] parameterValues,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        ArgumentUtils.requireNonNull("method", method);
        ArgumentUtils.requireNonNull("parameterValues", parameterValues);
        final Argument[] arguments = method.getArguments();
        final int argLen = arguments.length;
        if (parameterValues == null || argLen != parameterValues.length) {
            throw new IllegalArgumentException("The method parameter array must have exactly " + argLen + " elements.");
        }

        DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object);
        Set overallViolations = new HashSet<>(5);

        final Path.Node node = context.addMethodNode(method.getMethodName(), method.getArgumentTypes());
        try {
            for (int i = 0; i < argLen; i++) {
                Argument argument = arguments[i];
                final Class<?> parameterType = argument.getType();
                final AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
                Object parameterValue = parameterValues[i];

                ValueExtractor<Object> valueExtractor = null;
                final boolean hasValue = parameterValue != null;
                if (hasValue) {
                    //noinspection unchecked
                    valueExtractor = (ValueExtractor<Object>) valueExtractorRegistry.findUnwrapValueExtractor(parameterType).orElse(null);
                }

                int finalIndex = i;
                if (valueExtractor != null) {
                    valueExtractor.extractValues(parameterValue, (SimpleValueReceiver) (nodeName, unwrappedValue) -> validateParameterInternal(
                            object,
                            parameterValues,
                            context,
                            overallViolations,
                            argument.getName(),
                            parameterType,
                            finalIndex,
                            annotationMetadata,
                            unwrappedValue
                    ));
                } else {
                    validateParameterInternal(
                            object,
                            parameterValues,
                            context,
                            overallViolations,
                            argument.getName(),
                            parameterType,
                            finalIndex,
                            annotationMetadata,
                            parameterValue
                    );
                }

                if (hasValue && annotationMetadata.hasStereotype(Valid.class)) {
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
                                        object,
                                        parameterValue,
                                        node,
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
                                        object,
                                        parameterValue,
                                        node,
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
                                        object,
                                        parameterValue,
                                        node,
                                        argument,
                                        keyedValue,
                                        overallViolations,
                                        null,
                                        key,
                                        false);
                            }
                        });
                    } else {
                        final BeanIntrospection<Object> beanIntrospection = getBeanIntrospection(parameterValue);
                        if (beanIntrospection != null) {
                            cascadeToOneIntrospection(
                                    context,
                                    object,
                                    parameterValue,
                                    beanIntrospection,
                                    overallViolations
                            );
                        }
                    }
                }
            }
        } finally {
            context.removeLast();
        }
        return Collections.unmodifiableSet(overallViolations);
    }

    @SuppressWarnings("unchecked")
    private <T> void validateParameterInternal(
            @Nonnull T object,
            @Nonnull Object[] parameterValues,
            @Nonnull DefaultConstraintValidatorContext context,
            @Nonnull Set overallViolations,
            @Nonnull String parameterName,
            @Nonnull Class<?> parameterType,
            int parameterIndex,
            @Nonnull AnnotationMetadata annotationMetadata,
            @Nullable Object parameterValue) {
        try {
            context.addParameterNode(parameterName, parameterIndex);
            final List<Class<? extends Annotation>> constraintTypes =
                    annotationMetadata.getAnnotationTypesByStereotype(Constraint.class);
            for (Class<? extends Annotation> constraintType : constraintTypes) {
                final ConstraintValidator constraintValidator = constraintValidatorRegistry
                        .findConstraintValidator(constraintType, parameterType).orElse(null);
                if (constraintValidator != null) {
                    final AnnotationValue<? extends Annotation> annotationValue =
                            annotationMetadata.getAnnotation(constraintType);
                    if (annotationValue != null && !constraintValidator.isValid(parameterValue, annotationValue, context)) {
                        final String messageTemplate = buildMessageTemplate(annotationValue);
                        overallViolations.add(new DefaultConstraintViolation(
                                object,
                                object.getClass(),
                                null,
                                parameterValue,
                                messageTemplate,
                                messageTemplate,
                                new PathImpl(context.currentPath), parameterValues));
                    }
                }
            }
        } finally {
            context.removeLast();
        }
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateParameters(
            @Nonnull T object,
            @Nonnull Method method,
            @Nonnull Object[] parameterValues,
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

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateReturnValue(@Nonnull T object, @Nonnull Method method, @Nullable Object returnValue, @Nullable Class<?>... groups) {
        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorParameters(@Nonnull Constructor<? extends T> constructor, @Nonnull Object[] parameterValues, @Nullable Class<?>... groups) {
        // TODO: constructor validation
        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(@Nonnull Constructor<? extends T> constructor, @Nonnull T createdObject, @Nullable Class<?>... groups) {
        // TODO: constructor validation
        return Collections.emptySet();
    }

    /**
     * The context object.
     */
    private final class DefaultConstraintValidatorContext implements ConstraintValidatorContext {
        Set<Object> validatedObjects = new HashSet<>(20);
        PathImpl currentPath = new PathImpl();
        DefaultPropertyNode currentContainerNode;

        private <T> DefaultConstraintValidatorContext(T object) {
            validatedObjects.add(object);
        }

        private DefaultConstraintValidatorContext() {
        }

        @Nonnull
        @Override
        public ClockProvider getClockProvider() {
            return clockProvider;
        }

        Path.Node addPropertyNode(String name) {
            final DefaultPropertyNode node;
            if (currentContainerNode != null) {
                node = new DefaultPropertyNode(
                        name, currentContainerNode
                );
            } else {
                node = new DefaultPropertyNode(name, null, null, null, ElementKind.PROPERTY, false);
            }
            currentPath.nodes.add(node);
            return node;
        }

        Path.Node removeLast() {
            return currentPath.nodes.removeLast();
        }

        @SuppressWarnings("unchecked")
        Path.Node addMethodNode(String methodName, Class[] argumentTypes) {
            final DefaultMethodNode methodNode = new DefaultMethodNode(methodName, Arrays.asList(argumentTypes));
            currentPath.nodes.add(methodNode);
            return methodNode;
        }

        Path.Node addParameterNode(String name, int index) {
            final DefaultParameterNode node;
            if (currentContainerNode != null) {
                node = new DefaultParameterNode(
                        name, currentContainerNode, index
                );
            } else {
                node = new DefaultParameterNode(
                        name, index
                );
            }
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
     * Method node implementation.
     */
    private final class DefaultMethodNode implements Path.MethodNode {

        private final String name;
        private final List<Class<?>> parameterTypes;

        DefaultMethodNode(String name, List<Class<?>> parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public List<Class<?>> getParameterTypes() {
            return parameterTypes;
        }

        @Override
        public String getName() {
            return name;
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
        public <T extends Path.Node> T as(Class<T> nodeType) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }
    }

    /**
     * Method node implementation.
     */
    private final class DefaultParameterNode extends DefaultPropertyNode implements Path.ParameterNode {

        private final int parameterIndex;

        public DefaultParameterNode(@Nonnull String name, int parameterIndex) {
            super(name, null, null, null, ElementKind.PARAMETER, false);
            this.parameterIndex = parameterIndex;
        }

        public DefaultParameterNode(@Nonnull String name, @Nonnull DefaultPropertyNode parent, int index) {
            super(name, parent);
            this.parameterIndex = index;
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
                @Nonnull String name,
                @Nullable Class<?> containerClass,
                @Nullable Integer index,
                @Nullable Object key,
                @Nonnull ElementKind kind,
                boolean isIterable) {
            this.containerClass = containerClass;
            this.name = name;
            this.index = index;
            this.key = key;
            this.kind = kind;
            this.isIterable = isIterable || index != null;
        }

        DefaultPropertyNode(
                @Nonnull String name,
                @Nonnull DefaultPropertyNode parent
        ) {
            this(name, parent.containerClass, parent.getIndex(), parent.getKey(), ElementKind.CONTAINER_ELEMENT, parent.isInIterable());
        }

        DefaultPropertyNode(
                @Nonnull String name,
                @Nullable Class<?> containerClass,
                @Nullable Integer index,
                @Nullable Object key,
                @Nonnull ElementKind kind) {
            this(name, containerClass, index, key, kind, index != null);
        }

        DefaultPropertyNode(
                @Nonnull String name,
                @Nullable Class<?> containerClass,
                @Nullable Integer index,
                @Nullable Object key) {
            this(name, containerClass, index, key, ElementKind.PROPERTY);
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
        private final Object[] executableParams;

        private DefaultConstraintViolation(
                @Nullable T rootBean,
                @Nullable Class<T> rootBeanClass,
                Object leafBean,
                Object invalidValue,
                String message,
                String messageTemplate,
                Path path,
                Object... executableParams) {
            this.rootBean = rootBean;
            this.rootBeanClass = rootBeanClass;
            this.invalidValue = invalidValue;
            this.message = message;
            this.messageTemplate = messageTemplate;
            this.path = path;
            this.leafBean = leafBean;
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
            return executableParams;
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
            return null;
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
        }
    }
}
