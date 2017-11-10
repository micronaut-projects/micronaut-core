/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.annotation

import org.particleframework.context.annotation.Bean
import org.particleframework.context.annotation.ForEach
import org.particleframework.context.annotation.Primary
import org.particleframework.context.annotation.Requires
import org.particleframework.inject.BeanConfiguration
import org.particleframework.inject.BeanDefinition

import javax.inject.Scope
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class BeanDefinitionAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test build configuration"() {
        given:
        BeanConfiguration configuration = buildBeanConfiguration("test", '''
@Configuration
@Requires(property="foo")
package test;
import org.particleframework.context.annotation.*;

''')
        expect:
        configuration != null
        configuration.getAnnotationMetadata().hasStereotype(Requires)
    }

    void "test build bean basic definition"() {
        given:
        BeanDefinition definition = buildBeanDefinition("test.Test", '''
package test;

@javax.inject.Singleton
class Test {

}
''')
        expect:
        definition != null
        definition.hasDeclaredAnnotation(Singleton)
        definition.hasDeclaredStereotype(Scope)
        definition.hasStereotype(Scope)
        !definition.hasStereotype(Primary)
    }

    void "test factory bean definition"() {
        given:
        ClassLoader classLoader = buildClassLoader("test.Test", '''
package test;

import org.particleframework.context.annotation.*;
import java.util.concurrent.*;

@Factory
class Test {

    @ForEach(Test.class)
    @Bean(preDestroy = "shutdown")
    public ExecutorService executorService(Test test) {
        return null;
    }
}

''')
        BeanDefinition definition = classLoader.loadClass('test.$Test$ExecutorServiceDefinition').newInstance()
        expect:
        definition != null
        !definition.hasDeclaredAnnotation(Singleton)
        definition.hasDeclaredAnnotation(Bean)
        definition.hasDeclaredAnnotation(ForEach)
    }
}
