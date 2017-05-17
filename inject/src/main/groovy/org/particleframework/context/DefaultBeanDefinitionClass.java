package org.particleframework.context;

import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.context.exceptions.BeanContextException;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.BeanDefinitionClass;

/**
 * An uninitialized component definition with basic information available regarding its requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultBeanDefinitionClass<T> implements BeanDefinitionClass<T> {

    private final Class<? extends BeanDefinition<T>> componentDefinitionClass;

    protected DefaultBeanDefinitionClass(Class<? extends BeanDefinition<T>> componentDefinitionClass) {
        this.componentDefinitionClass = componentDefinitionClass;
    }

    /**
     * @return The actual type of the component
     */
    @Override
    public Class<T> getComponentType() {
        Class componentType = GenericTypeUtils.resolveSuperGenericTypeArgument(componentDefinitionClass);
        if(componentType == null) {
            throw new IllegalStateException("Invalid component definition class ["+componentDefinitionClass.getName()+"] found on classpath");
        }
        return componentType;
    }

    /**
     * @return The loaded component definition
     */
    @Override
    public BeanDefinition<T> load() {
        try {
            return componentDefinitionClass.newInstance();
        } catch (Throwable e) {
            throw new BeanContextException("Error loading component definition ["+componentDefinitionClass.getName()+"]: " + e.getMessage(), e);
        }
    }
}
