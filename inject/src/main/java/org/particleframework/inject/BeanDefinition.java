package org.particleframework.inject;

import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanResolutionContext;
import org.particleframework.core.annotation.AnnotationMetadataDelegate;
import org.particleframework.core.naming.Named;
import org.particleframework.core.reflect.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines a bean definition and its requirements. A bean definition must have a singled injectable constructor or a no-args constructor.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinition<T> extends AnnotationMetadataDelegate, Named, BeanType<T> {

    /**
     * @return The scope of the component
     */
    Class<? extends Annotation> getScope();

    /**
     * @return Whether the scope is singleton
     */
    boolean isSingleton();

    /**
     * @return Is this definition provided by another bean
     */
    boolean isProvided();

    /**
     * @return Whether the bean declared {@link org.particleframework.context.annotation.ForEach}
     */
    boolean isIterable();

    /**
     * @return The component type
     */
    @Override
    Class<T> getBeanType();

    /**
     * The single concrete constructor that is an injection point for creating the bean.
     *
     * @return The constructor injection point
     */
    ConstructorInjectionPoint<T> getConstructor();

    /**
     * @return All required components for this entity definition
     */
    Collection<Class> getRequiredComponents();

    /**
     * All methods that require injection. This is a subset of all the methods in the class.
     *
     * @return The required properties
     */
    Collection<MethodInjectionPoint> getInjectedMethods();

    /**
     * All the fields that require injection.
     *
     * @return The required fields
     */
    Collection<FieldInjectionPoint> getInjectedFields();

    /**
     * All the methods that should be called once the bean has been fully initialized and constructed
     *
     * @return Methods to call post construct
     */
    Collection<MethodInjectionPoint> getPostConstructMethods();

    /**
     * All the methods that should be called when the object is to be destroyed
     *
     * @return Methods to call pre-destroy
     */
    Collection<MethodInjectionPoint> getPreDestroyMethods();

    /**
     * @return The class name
     */
    String getName();

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @return An optional {@link ExecutableMethod}
     */
    <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class... argumentTypes);

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @return An optional {@link ExecutableMethod}
     * @throws IllegalStateException If the method cannot be found
     */
    @SuppressWarnings("unchecked")
    default <R> ExecutableMethod<T, R> getRequiredMethod(String name, Class... argumentTypes) {
        return (ExecutableMethod<T, R>) findMethod(name, argumentTypes)
                .orElseThrow(() -> ReflectionUtils.newNoSuchMethodError(getBeanType(), name, argumentTypes));
    }

    /**
     * Finds possible methods for the given method name
     *
     * @param name The method name
     * @return The possible methods
     */
    <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name);

    /**
     * Inject the given bean with the context
     *
     * @param context The context
     * @param bean The bean
     * @return The injected bean
     */
    T inject(BeanContext context, T bean);

    /**
     * Inject the given bean with the context
     *
     * @param resolutionContext the resolution context
     * @param context The context
     * @param bean The bean
     * @return The injected bean
     */
    T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean);

    /**
     * @return The {@link ExecutableMethod} instances for this definition
     */
    Collection<ExecutableMethod<T,?>> getExecutableMethods();
}