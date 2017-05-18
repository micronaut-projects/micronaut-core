package org.particleframework.context;

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

    protected AbstractBeanConfiguration(Package thePackage) {
        this.thePackage = thePackage;
        this.packageName = thePackage.getName();
    }

    @Override
    public Package getPackage() {
        return thePackage;
    }

    @Override
    public boolean isEnabled(BeanContext context) {
        // TODO: Implement support for requirements
        return true;
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
