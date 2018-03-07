package io.micronaut.context;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An uninitialized and unloaded component definition with basic information available regarding its requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractBeanDefinitionReference extends AbstractBeanContextConditional implements BeanDefinitionReference {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinitionReference.class);
    private final String beanTypeName;
    private final String beanDefinitionTypeName;
    private Class beanDefinition;
    private Boolean present;

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
                throw new BeanInstantiationException("Error loading bean definition [" + beanTypeName + "]: " + e.getMessage(), e);
            }
        } else {
            throw new BeanInstantiationException("Cannot load bean for type [" + beanTypeName + "]. The type is not present on the classpath");
        }
    }

    @Override
    public BeanDefinition load(BeanContext context) {
        BeanDefinition definition = load();
        ((AbstractBeanDefinition)definition).configure(context);
        return definition;
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
        return isPresent() && super.isEnabled(beanContext);
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
