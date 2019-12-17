/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinitionReference;

/**
 * An abstract implementation of the {@link BeanConfiguration} method. Not typically used directly from user code,
 * instead an implementation will perform analysis on package-info files generate a configuration definition for a
 * given package.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractBeanConfiguration extends AbstractBeanContextConditional implements BeanConfiguration {

    private final String packageName;

    /**
     * @param thePackage The package name
     */
    protected AbstractBeanConfiguration(String thePackage) {
        this.packageName = thePackage;
    }

    @Override
    public Package getPackage() {
        return getClass().getPackage();
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
        final int i = className.lastIndexOf('.');
        String pkgName = i > -1 ? className.substring(0, i) : className;
        return pkgName.equals(this.packageName) || pkgName.startsWith(this.packageName + '.');
    }
}
