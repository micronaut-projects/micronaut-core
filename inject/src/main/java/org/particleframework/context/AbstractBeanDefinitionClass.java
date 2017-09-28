package org.particleframework.context;

import org.particleframework.context.annotation.Requirements;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.RequiresCondition;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.context.exceptions.BeanContextException;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.context.annotation.Context;
import org.particleframework.inject.BeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An uninitialized and unloaded component definition with basic information available regarding its requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractBeanDefinitionClass implements BeanDefinitionClass {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinitionClass.class);
    private final String beanTypeName;
    private final String beanDefinitionTypeName;
    private Class beanDefinition;
    private Boolean present;

    public AbstractBeanDefinitionClass(String beanTypeName, String beanDefinitionTypeName) {
        this.beanTypeName = beanTypeName;
        this.beanDefinitionTypeName = beanDefinitionTypeName;
    }

    /**
     * @return The actual type of the component
     */
    @Override
    public Class getBeanType() {
        if (isPresent()) {
            return GenericTypeUtils.resolveInterfaceTypeArgument(beanDefinition, BeanFactory.class)
                    .orElse(null);
        }
        return null;
    }

    @Override
    public String getReplacesBeanTypeName() {
        return null; // no replacement semantics by default
    }

    @Override
    public String getReplacesBeanDefinitionName() {
        return null; // no replacement
    }

    /**
     * @return The loaded component definition
     */
    @Override
    public BeanDefinition load() {
        if (isPresent()) {
            try {
                return (BeanDefinition) beanDefinition.newInstance();
            } catch (Throwable e) {
                throw new BeanContextException("Error loading bean definition [" + beanTypeName + "]: " + e.getMessage(), e);
            }
        } else {
            throw new BeanContextException("Cannot load bean for type [" + beanTypeName + "]. The type is not present on the classpath");
        }
    }

    @Override
    public boolean isContextScope() {
        return getClass().getAnnotation(Context.class) != null;
    }

    @Override
    public String getBeanTypeName() {
        return beanTypeName;
    }

    @Override
    public boolean isPresent() {
        if(present == null) {
            loadType();
        }
        return present;
    }

    @Override
    public boolean isEnabled(BeanContext beanContext) {
        if (isPresent()) {

            Class<?> beanType = getBeanType();
            if(beanType != null) {

                Requires[] annotations = beanType.getAnnotationsByType(Requires.class);
                if (annotations.length == 0) {
                    Requirements requirements = beanType.getAnnotation(Requirements.class);
                    if (requirements != null) {
                        annotations = requirements.value();
                    }
                }
                Condition condition = annotations.length == 0 ? null : new RequiresCondition(annotations);
                return condition == null || condition.matches(new DefaultConditionContext<>(beanContext, this));
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractBeanDefinitionClass that = (AbstractBeanDefinitionClass) o;

        return beanDefinitionTypeName.equals(that.beanDefinitionTypeName);
    }

    @Override
    public String toString() {
        return beanDefinitionTypeName;
    }

    @Override
    public int hashCode() {
        return beanDefinitionTypeName.hashCode();
    }

    private void loadType() {
        if (present == null && beanDefinition == null) {

            try {
                beanDefinition = Class.forName(beanDefinitionTypeName, false, getClass().getClassLoader());
                present = true;
            } catch (ClassNotFoundException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Bean definition for type [" + beanTypeName + "] not loaded since it is not on the classpath", e);
                }
                present = false;
            }
        }
    }
}
