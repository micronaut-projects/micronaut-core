package org.particleframework.inject;

import org.particleframework.context.BeanContext;
import org.particleframework.core.annotation.AnnotationSource;
import org.particleframework.core.annotation.Internal;

/**
 * <p>A component definition class provides a reference to a {@link BeanDefinition} thus
 * allowing for soft loading of component definitions for purposes of type inspection.</p>
 *
 * <p>The class can also decided whether to abort loading the definition by returning null</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface BeanDefinitionClass<T> extends AnnotationSource {
    /**
     * @return The underlying component type
     */
    Class<T> getBeanType();

    /**
     * @return The name of the bean type
     */
    String getBeanTypeName();

    /**
     * @return The name of the bean that this bean replaces
     */
    String getReplacesBeanTypeName();

    /**
     * @return The name of the bean definition that this bean replaces
     */
    String getReplacesBeanDefinitionName();

    /**
     * Loads the component definition
     *
     * @return The loaded component definition or null if it shouldn't be loaded
     */
    BeanDefinition<T> load();

    /**
     * @return Is this class context scope
     */
    boolean isContextScope();

    /**
     * @return Is the underlying bean type present on the classpath
     */
    boolean isPresent();

    /**
     * Whether the bean is enabled
     *
     * @param beanContext The bean context
     * @return True if it is
     */
    boolean isEnabled(BeanContext beanContext);
}
