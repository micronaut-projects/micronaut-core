/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test


import io.micronaut.context.ApplicationContext
import io.micronaut.context.Qualifier
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import org.intellij.lang.annotations.Language
import spock.lang.Specification

class AbstractKotlinCompilerSpec extends Specification {
    protected ClassLoader buildClassLoader(String className, @Language("kotlin") String cls) {
        KotlinCompiler.buildClassLoader(className, cls)
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     * */
    protected BeanIntrospection buildBeanIntrospection(String className, @Language("kotlin") String cls) {
        def beanDefName = '$' + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanIntrospection) classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     */
    protected ApplicationContext buildContext(String className, @Language("kotlin") String cls, boolean includeAllBeans = false) {
        KotlinCompiler.buildContext(cls, includeAllBeans)
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     */
    protected ApplicationContext buildContext(@Language("kotlin") String cls, boolean includeAllBeans = false) {
        KotlinCompiler.buildContext(cls, includeAllBeans)
    }

    Object getBean(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBean(context.classLoader.loadClass(className), qualifier)
    }

    protected BeanDefinition buildBeanDefinition(String className, @Language("kotlin") String cls) {
        KotlinCompiler.buildBeanDefinition(className, cls)
    }
}
