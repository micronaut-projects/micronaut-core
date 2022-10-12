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
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor
/**
 * @author graemerocher
 * @since 1.0
 */
class AbstractClassIntroductionSpec extends AbstractBeanDefinitionSpec {

    void "test that a non-abstract method defined in class is not overridden by the introduction advise"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean {
    public abstract String isAbstract();

    public String nonAbstract() {
        return "good";
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

interface Foo {
    String nonAbstract();
}
@Stub
@jakarta.inject.Singleton
abstract class AbstractBean implements Foo {
    public abstract String isAbstract();

    @Override
    public String nonAbstract() {
        return "good";
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise that also defines advice on the method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

interface Foo {
    @Stub
    String nonAbstract();
}
@Stub
@jakarta.inject.Singleton
abstract class AbstractBean implements Foo {
    public abstract String isAbstract();

    @Override
    public String nonAbstract() {
        return "good";
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise that also defines advice on a super interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

interface Bar {
    @Stub
    String nonAbstract();

    String another();
}


interface Foo extends Bar {
}

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean implements Foo {
    public abstract String isAbstract();

    @Override
    public String nonAbstract() {
        return "good";
    }

    @Override
    public String another() {
        return "good";
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
        instance.another() == 'good'
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise that also defines advice on the class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@Stub
interface Foo {
    String nonAbstract();
}
@Stub
@jakarta.inject.Singleton
abstract class AbstractBean implements Foo {
    public abstract String isAbstract();

    @Override
    public String nonAbstract() {
        return "good";
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        when:
        def context = new DefaultBeanContext()
        context.start()
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
    }

}
