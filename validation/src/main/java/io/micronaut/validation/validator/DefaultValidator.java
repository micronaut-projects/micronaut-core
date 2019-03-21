package io.micronaut.validation.validator;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.*;
import javax.validation.metadata.ConstraintDescriptor;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Default implementation of the {@link Validator} interface.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class DefaultValidator implements Validator {

    private final ConstraintValidatorRegistry constraintValidatorRegistry;

    /**
     * Default constructor.
     *
     * @param constraintValidatorRegistry The validator registry.
     */
    @Inject
    public DefaultValidator(
            @Nonnull ConstraintValidatorRegistry constraintValidatorRegistry) {
        ArgumentUtils.requireNonNull("constraintValidatorRegistry", constraintValidatorRegistry);
        this.constraintValidatorRegistry = constraintValidatorRegistry;
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validate(@Nullable T object, @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        @SuppressWarnings("unchecked") final BeanIntrospection<T> introspection = (BeanIntrospection<T>) BeanIntrospector.SHARED.findIntrospection(object.getClass())
                .orElseThrow(() -> new ValidationException("Passed object [" + object + "] cannot be introspected. Please annotation with @Introspected"));
        final Collection<? extends BeanProperty<T, Object>> constrainedProperties = introspection.getIndexedProperties(Constraint.class);

        if (CollectionUtils.isNotEmpty(constrainedProperties)) {
            DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object);
            Set<ConstraintViolation<T>> overallViolations = new HashSet<>(5);
            for (BeanProperty<T, Object> constrainedProperty : constrainedProperties) {
                final Object propertyValue = constrainedProperty.get(object);

                validateConstrainedPropertyInternal(
                        introspection,
                        object,
                        constrainedProperty,
                        propertyValue,
                        context,
                        overallViolations
                );
            }

            return Collections.unmodifiableSet(overallViolations);
        }


        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(
            @Nullable T object,
            @Nonnull String propertyName,
            @Nullable Class<?>... groups) {
        ArgumentUtils.requireNonNull("object", object);
        ArgumentUtils.requireNonNull("propertyName", propertyName);
        @SuppressWarnings("unchecked") final BeanIntrospection<T> introspection = (BeanIntrospection<T>) BeanIntrospector.SHARED.findIntrospection(object.getClass())
                .orElseThrow(() -> new ValidationException("Passed object [" + object + "] cannot be introspected. Please annotation with @Introspected"));

        final Optional<BeanProperty<T, Object>> property = introspection.getProperty(propertyName);

        if (property.isPresent()) {
            DefaultConstraintValidatorContext context = new DefaultConstraintValidatorContext(object);
            final BeanProperty<T, Object> constrainedProperty = property.get();
            Set<ConstraintViolation<T>> overallViolations = new HashSet<>(5);
            final Object propertyValue = constrainedProperty.get(object);

            validateConstrainedPropertyInternal(
                    introspection,
                    object,
                    constrainedProperty,
                    propertyValue,
                    context,
                    overallViolations
            );

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

        final BeanIntrospection<T> introspection = BeanIntrospector.SHARED.findIntrospection(beanType)
                .orElseThrow(() -> new ValidationException("Passed bean type [" + beanType + "] cannot be introspected. Please annotation with @Introspected"));

        final BeanProperty<T, Object> beanProperty = introspection.getProperty(propertyName)
                .orElseThrow(() -> new ValidationException("No property [" + propertyName + "] found on type: " + beanType));


        final HashSet<ConstraintViolation<T>> overallViolations = new HashSet<>(5);
        validatePropertyInternal(null, new DefaultConstraintValidatorContext(), overallViolations, beanProperty, value);
        return Collections.unmodifiableSet(overallViolations);
    }

    private <T> void validateConstrainedPropertyInternal(
            BeanIntrospection<T> introspection,
            @Nonnull T object,
            BeanProperty<T, Object> constrainedProperty,
            Object propertyValue,
            DefaultConstraintValidatorContext context,
            Set<ConstraintViolation<T>> overallViolations) {
        context.currentPath.addPropertyNode(
                introspection.getBeanType(),
                constrainedProperty.getName(),
                null,
                null
        );

        validatePropertyInternal(
                object,
                context,
                overallViolations,
                constrainedProperty,
                propertyValue
        );
        context.currentPath.nodes.pop();
    }

    private <T> void validatePropertyInternal(@Nullable T object, DefaultConstraintValidatorContext context, Set<ConstraintViolation<T>> overallViolations, BeanProperty<T, Object> constrainedProperty, Object propertyValue) {
        final List<Class<? extends Annotation>> constraintTypes = constrainedProperty.getAnnotationTypesByStereotype(Constraint.class);
        for (Class<? extends Annotation> constraintType : constraintTypes) {
            final List<? extends AnnotationValue<? extends Annotation>> annotationValues = constrainedProperty.getAnnotationValuesByType(constraintType);

            final ConstraintValidator<? extends Annotation, Object> validator = constraintValidatorRegistry.getConstraintValidator(constraintType, constrainedProperty.getType());
            for (AnnotationValue annotationValue : annotationValues) {
                if (!validator.isValid(propertyValue, annotationValue, context)) {

                    final String messageTemplate = (String) annotationValue.get("message", String.class)
                            .orElse("{" + annotationValue.getAnnotationName() +  ".message}");
                    overallViolations.add(
                            new DefaultConstraintViolation<>(
                                    object,
                                    (Class<T>) object.getClass(),
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

    /**
     * The context object.
     */
    private final class DefaultConstraintValidatorContext implements ConstraintValidatorContext {
        Set<Object> validatedObjects = new HashSet<>(20);
        PathImpl currentPath = new PathImpl();

        private <T> DefaultConstraintValidatorContext(T object) {
            validatedObjects.add(object);
        }

        private DefaultConstraintValidatorContext() {
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

        void addPropertyNode(
                @Nonnull Class<?> containerClass,
                @Nonnull String name,
                @Nullable Integer index,
                @Nullable Object key) {
            nodes.push(new PropertyNode() {
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
                    return index != null;
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
                    return ElementKind.PROPERTY;
                }

                @Override
                public <T extends Node> T as(Class<T> nodeType) {
                    throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
                }
            });
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

        private DefaultConstraintViolation(
                @Nullable T rootBean,
                @Nullable Class<T> rootBeanClass,
                Object invalidValue,
                String message,
                String messageTemplate,
                Path path) {
            this.rootBean = rootBean;
            this.rootBeanClass = rootBeanClass;
            this.invalidValue = invalidValue;
            this.message = message;
            this.messageTemplate = messageTemplate;
            this.path = path;
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

        @SuppressWarnings("unchecked")
        @Override
        public Class<T> getRootBeanClass() {
            return rootBeanClass;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return null;
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
