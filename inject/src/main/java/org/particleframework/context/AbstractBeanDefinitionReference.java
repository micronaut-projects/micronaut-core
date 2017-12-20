package org.particleframework.context;

import org.particleframework.context.annotation.Context;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Requirements;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.RequiresCondition;
import org.particleframework.context.exceptions.BeanContextException;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionReference;
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
public abstract class AbstractBeanDefinitionReference implements BeanDefinitionReference {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinitionReference.class);
    private final String beanTypeName;
    private final String beanDefinitionTypeName;
    private Class beanDefinition;
    private Boolean present;
    private Boolean enabled;

    public AbstractBeanDefinitionReference(String beanTypeName, String beanDefinitionTypeName) {
        this.beanTypeName = beanTypeName;
        this.beanDefinitionTypeName = beanDefinitionTypeName;
    }

    @Override
    public boolean isPrimary() {
        return getAnnotationMetadata().hasAnnotation(Primary.class);
    }

    @Override
    public String getName() {
        return beanTypeName;
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
    public String getBeanDefinitionName() {
        return beanDefinitionTypeName;
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
            if(enabled == null) {
                AnnotationMetadata annotationMetadata = getAnnotationMetadata();
                Condition condition = annotationMetadata.hasStereotype(Requirements.class) || annotationMetadata.hasStereotype(Requires.class)? new RequiresCondition(annotationMetadata) : null;
                enabled = condition == null || condition.matches(new DefaultConditionContext<>(beanContext, this));
            }
            return enabled;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractBeanDefinitionReference that = (AbstractBeanDefinitionReference) o;

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
                GenericTypeUtils.resolveInterfaceTypeArgument(beanDefinition, BeanFactory.class);
                present = true;
            } catch (TypeNotPresentException | ClassNotFoundException | NoClassDefFoundError e) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Bean definition for type [" + beanTypeName + "] not loaded since it is not on the classpath", e);
                }
                present = false;
            }
        }
    }
}
