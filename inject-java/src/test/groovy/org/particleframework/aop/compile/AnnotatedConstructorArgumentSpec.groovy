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
package org.particleframework.aop.compile

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.AbstractTypeElementSpec
import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.BeanFactory
import org.particleframework.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class AnnotatedConstructorArgumentSpec extends AbstractTypeElementSpec{


    void "test that constructor arguments propagate annotation metadata"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import org.particleframework.aop.simple.*;
import org.particleframework.context.annotation.*;

@Mutating("someVal")
@javax.inject.Singleton
class MyBean {

    private String myValue;
    
    MyBean(@Value("foo.bar") String val) {
        this.myValue = val;
    }
    
    public String someMethod() {
        return myValue;
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        when:
        def context = ApplicationContext.run('foo.bar':'test')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.someMethod() == 'test'
    }
}
