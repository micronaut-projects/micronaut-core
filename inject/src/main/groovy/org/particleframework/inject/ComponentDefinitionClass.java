package org.particleframework.inject;

/**
 * Created by graemerocher on 11/05/2017.
 */
public interface ComponentDefinitionClass<T> {
    Class<T> getComponentType();

    ComponentDefinition<T> load();
}
