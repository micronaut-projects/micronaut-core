package org.particleframework.context;

import org.particleframework.context.annotation.Requires;
import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.RequiresCondition;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinitionClass;

import java.util.ArrayList;
import java.util.Collection;

/**
 * An abstract implementation of the {@link BeanConfiguration} method. Not typically used directly from user code, instead
 * an implementation will perform analysis on package-info files generate a configuration definition for a given package.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AbstractBeanConfiguration implements BeanConfiguration {

    private final Package thePackage;
    private final String packageName;
    private final Collection<BeanDefinitionClass> beanDefinitionClasses = new ArrayList<>();
    private final Condition condition;
    private Boolean enabled = null;

    protected AbstractBeanConfiguration(Package thePackage) {
        this.thePackage = thePackage;
        this.packageName = thePackage.getName();
        Requires[] annotations = thePackage.getAnnotationsByType(Requires.class);
        this.condition = annotations.length == 0 ? null : new RequiresCondition(annotations);
    }

    @Override
    public Package getPackage() {
        return thePackage;
    }

    @Override
    public String getName() {
        return this.packageName;
    }

    @Override
    public String getVersion() {
        return getPackage().getImplementationVersion();
    }

    @Override
    public boolean isEnabled(BeanContext context) {
        if(enabled == null) {
            enabled = condition.matches(new DefaultConditionContext(context, this));
        }
        return enabled;
    }

    @Override
    public boolean isWithin(BeanDefinitionClass beanDefinitionClass) {
        String beanTypeName = beanDefinitionClass.getBeanTypeName();
        return beanTypeName.startsWith(packageName);
    }

    Collection<BeanDefinitionClass> getBeanDefinitionClasses() {
        return beanDefinitionClasses;
    }

    AbstractBeanConfiguration addDefinitionClass(BeanDefinitionClass definitionClass) {
        beanDefinitionClasses.add(definitionClass);
        return this;
    }
}
