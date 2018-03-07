package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationSource;
import io.micronaut.core.annotation.Internal;

/**
 * <p>A bean definition reference provides a reference to a {@link BeanDefinition} thus
 * allowing for soft loading of bean definitions without loading the actual types.</p>
 *
 * <p>This interface implements {@link AnnotationMetadataProvider} thus allowing the bean metadata to be introspected safely
 * without loading the class or the annotations themselves.</p>
 *
 * <p>The actual bean will be loaded upon calling the {@link #load()} method. Note that consumers of this interface should call {@link #isPresent()} prior to loading to ensure an error does not occur</p>
 *
 * <p>The class can also decided whether to abort loading the definition by returning null</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface BeanDefinitionReference<T> extends BeanType<T> {

    /**
     * @return The class name of the backing {@link BeanDefinition}
     */
    String getBeanDefinitionName();

    /**
     * @return The name of the bean that this bean replaces
     */
    String getReplacesBeanTypeName();

    /**
     * @return The name of the bean definition that this bean replaces
     */
    String getReplacesBeanDefinitionName();

    /**
     * Loads the bean definition
     *
     * @return The loaded component definition or null if it shouldn't be loaded
     */
    BeanDefinition<T> load();

    /**
     * Loads the bean definition for the current {@link BeanContext}
     *
     * @return The loaded bean definition or null if it shouldn't be loaded
     */
    BeanDefinition<T> load(BeanContext context);

    /**
     * @return Is this class context scope
     */
    boolean isContextScope();

    /**
     * @return Is the underlying bean type present on the classpath
     */
    boolean isPresent();



}
