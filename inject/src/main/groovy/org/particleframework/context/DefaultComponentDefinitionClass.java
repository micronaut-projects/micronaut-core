package org.particleframework.context;

import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.ComponentDefinition;
import org.particleframework.context.exceptions.ContextException;
import org.particleframework.core.annotation.Internal;

/**
 * An uninitialized component definition with basic information available regarding its requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultComponentDefinitionClass<T> implements org.particleframework.inject.ComponentDefinitionClass<T> {

    private final Class<? extends ComponentDefinition<T>> componentDefinitionClass;

    protected DefaultComponentDefinitionClass(Class<? extends ComponentDefinition<T>> componentDefinitionClass) {
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
    public ComponentDefinition<T> load() {
        try {
            return componentDefinitionClass.newInstance();
        } catch (Throwable e) {
            throw new ContextException("Error loading component definition ["+componentDefinitionClass.getName()+"]: " + e.getMessage(), e);
        }
    }
}
