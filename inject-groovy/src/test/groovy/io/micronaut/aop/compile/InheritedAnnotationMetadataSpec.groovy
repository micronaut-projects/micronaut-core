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
package io.micronaut.aop.compile

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Blocking
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter

/**
 * @author graemerocher
 * @since 1.0
 */
class InheritedAnnotationMetadataSpec extends AbstractBeanDefinitionSpec {
    void "test that annotation metadata is inherited from overridden methods for introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('inheritmetadatatest1.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package inheritmetadatatest1;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;

@Stub
@jakarta.inject.Singleton
interface MyBean extends MyInterface {
    @Override
    public String someMethod();
}

interface MyInterface {
    @Blocking
    @Executable
    public String someMethod();
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].hasAnnotation(Blocking)

    }

    void "test that annotation metadata is inherited from overridden methods for around advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('inheritmetadatatest2.$MyBean2' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package inheritmetadatatest2;

import io.micronaut.aop.interceptors.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;

@Mutating("someVal")
@jakarta.inject.Singleton
class MyBean2 implements MyInterface2 {

    private String myValue;
    
    MyBean2(@Value('${foo.bar}') String val) {
        this.myValue = val;
    }
    
    @Override
    public String someMethod() {
        return myValue;
    }

}

interface MyInterface2 {
    @Blocking
    @Executable
    public String someMethod();
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].hasAnnotation(Blocking)
    }

    void "test that a bean definition is not created for an abstract class"() {
        when:
        ApplicationContext ctx = buildContext('''
package inheritmetadatatest3;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.core.order.Ordered;
import java.lang.annotation.*;
import jakarta.inject.Singleton;

interface ContractService {

    @SomeAnnot
    void interfaceServiceMethod();
}

abstract class BaseService {

    @SomeAnnot
    public void baseServiceMethod() {}
}

@SomeAnnot
abstract class BaseAnnotatedService {

}

@Singleton
class Service extends BaseService implements ContractService {

    @SomeAnnot
    public void serviceMethod() {}
    
    public void interfaceServiceMethod() {}
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER])
@Around
@Type(SomeInterceptor.class)
@interface SomeAnnot {}

@Singleton
class SomeInterceptor implements MethodInterceptor<Object, Object>, Ordered {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

''')
        then:
        Class clazz = ctx.classLoader.loadClass("inheritmetadatatest3.ContractService")
        ctx.getBean(clazz)

        when:
        ctx.classLoader.loadClass("inheritmetadatatest3.\$BaseService" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX)

        then:
        thrown(ClassNotFoundException)

        when:
        ctx.classLoader.loadClass("inheritmetadatatest3.\$BaseService" + BeanDefinitionWriter.CLASS_SUFFIX)

        then:
        thrown(ClassNotFoundException)

        when:
        ctx.classLoader.loadClass("inheritmetadatatest3.\$BaseAnnotatedService" + BeanDefinitionWriter.CLASS_SUFFIX)

        then:
        thrown(ClassNotFoundException)
    }
}
