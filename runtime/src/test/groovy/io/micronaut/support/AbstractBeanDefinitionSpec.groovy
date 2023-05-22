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
package io.micronaut.support

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Specification
/**
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractBeanDefinitionSpec extends Specification {

    @CompileStatic
    BeanDefinition buildBeanDefinition(String className, String classStr) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + BeanDefinitionWriter.CLASS_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        def classLoader = new InMemoryByteCodeGroovyClassLoader()
        classLoader.parseClass(classStr)
        return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
    }

}
