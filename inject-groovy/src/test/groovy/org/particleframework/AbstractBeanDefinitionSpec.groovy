/*
 * Copyright 2018 original authors
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
package org.particleframework

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilerConfiguration
import org.particleframework.ast.groovy.InjectTransform
import org.particleframework.core.naming.NameUtils
import org.particleframework.inject.BeanDefinition
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractBeanDefinitionSpec extends Specification {

    @CompileStatic
    BeanDefinition buildBeanDefinition(String className, String classStr) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + 'Definition'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        def configuration = new CompilerConfiguration()
        configuration.optimizationOptions.put(InjectTransform.PARTICLE_DEFINE_CLASSES, true)
        def classLoader = new GroovyClassLoader(getClass().getClassLoader(), configuration)
        classLoader.parseClass(classStr)
        return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
    }
}
