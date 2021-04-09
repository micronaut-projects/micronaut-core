package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.AbstractBeanConstructor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;

import java.util.Objects;

/**
 * Abstract constructor implementation for bean definitions to implement to create constructors at build time.
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@UsedByGeneratedCode
public abstract class AbstractConstructorInjectionPoint<T> extends AbstractBeanConstructor<T> implements ConstructorInjectionPoint<T>, EnvironmentConfigurable {
    private final BeanDefinition<T> beanDefinition;

    /**
     * Default constructor.
     *
     * @param beanDefinition           The bean type
     */
    protected AbstractConstructorInjectionPoint(BeanDefinition<T> beanDefinition) {
        super(
                Objects.requireNonNull(beanDefinition, "Bean definition cannot be null").getBeanType(),
                new AnnotationMetadataHierarchy(
                        beanDefinition.getAnnotationMetadata(),
                        beanDefinition.getConstructor().getAnnotationMetadata()),
                beanDefinition.getConstructor().getArguments()
        );
        this.beanDefinition = Objects.requireNonNull(beanDefinition, "Bean definition is required");
    }

    @Override
    @NonNull
    public final BeanDefinition<T> getDeclaringBean() {
        return beanDefinition;
    }

    @Override
    public final boolean requiresReflection() {
        return false;
    }
}
