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

import org.particleframework.context.AbstractExecutableMethod
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
class InjectFieldAbstractIntroductionSpec extends AbstractTypeElementSpec {

    void "that that you can field inject an abstract class that contains introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import org.particleframework.aop.introduction.*;
import org.particleframework.context.annotation.*;
import javax.inject.*;
@Stub
@Singleton
abstract class AbstractBean {
    protected @Value("something") String foo;
    protected @Inject SomeOther someOther;
    
    @Inject public void setFoo( SomeOther foo) {}
    @Inject public void setValue( @Value("something") String val) {}  
    public abstract String isAbstract(); 
    
    public String nonAbstract() {
        return "good";
    }

}

class SomeOther {}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 2
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.findMethod("nonAbstract").isPresent()
        // shouldn't be reflective access
        !beanDefinition.findMethod("nonAbstract").get().getClass().name.contains("ReflectionExecutableMethod")

    }
}
