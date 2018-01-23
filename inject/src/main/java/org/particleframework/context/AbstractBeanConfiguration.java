package org.particleframework.context;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinitionReference;

/**
 * An abstract implementation of the {@link BeanConfiguration} method. Not typically used directly from user code, instead
 * an implementation will perform analysis on package-info files generate a configuration definition for a given package.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AbstractBeanConfiguration extends AbstractBeanContextConditional implements BeanConfiguration {

    private final String packageName;

    protected AbstractBeanConfiguration(String thePackage) {
        this.packageName = thePackage.intern();
    }

    @Override
    public Package getPackage() {
        return Package.getPackage(packageName);
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
    public boolean isWithin(BeanDefinitionReference beanDefinitionReference) {
        String beanTypeName = beanDefinitionReference.getBeanDefinitionName();
        return isWithin(beanTypeName);
    }

    @Override
    public String toString() {
        return "Configuration: " + getName();
    }

    @Override
    public boolean isWithin(String className) {
        String pkgName = NameUtils.getPackageName(className);
        return pkgName.equals(this.packageName) || pkgName.startsWith(this.packageName + '.');
    }

}
