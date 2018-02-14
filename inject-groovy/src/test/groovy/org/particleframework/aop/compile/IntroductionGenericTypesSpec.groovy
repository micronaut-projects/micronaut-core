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

import org.particleframework.AbstractBeanDefinitionSpec
import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.writer.BeanDefinitionVisitor

/**
 * @author graemerocher
 * @since 1.0
 */
class IntroductionGenericTypesSpec extends AbstractBeanDefinitionSpec {
    void "test that generic return types are correct when implementing an interface with type arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import org.particleframework.aop.introduction.*;
import org.particleframework.context.annotation.*;
import java.net.*;

interface MyInterface<T extends URL> {

    T getURL();
    
    java.util.List<T> getURLs();
}


@Stub
@javax.inject.Singleton
interface MyBean extends MyInterface<URL> {
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.executableMethods[0].methodName == 'getURL'
        beanDefinition.executableMethods[0].returnType.type == URL
        beanDefinition.executableMethods[1].returnType.type == List
        beanDefinition.executableMethods[1].returnType.asArgument().hasTypeVariables()
        beanDefinition.executableMethods[1].returnType.asArgument().typeVariables['E'].type == URL
    }
}
