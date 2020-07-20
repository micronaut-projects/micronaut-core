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
package io.micronaut.inject.beans

import io.micronaut.aop.Intercepted
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

/**
 * @author graemerocher
 * @since 1.0
 */
class AbstractBeanSpec extends AbstractTypeElementSpec {

    void "test that abstract bean definitions are built for abstract classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean', '''
package test;

import io.micronaut.context.annotation.*;

@javax.inject.Singleton
abstract class AbstractBean {
    @Value("server.host")
    String host;

}
''')
        then:
        beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 1
    }

    void "test getBeansOfType filters proxy targets"() {
        when:
        def ctx = DefaultBeanContext.run()
        def targetBean = ctx.getProxyTargetBean(InterceptedBean, null)
        def bean = ctx.getBean(InterceptedBean)


        then:
        bean instanceof Intercepted
        targetBean != bean
        ctx.getBeansOfType(InterceptedBean).size() == 1
        targetBean != null
        bean != null

        cleanup:
        ctx.close()
    }

    void "test getBeansOfType filters proxy targets with context scoped beans"() {
        when:
        def ctx = DefaultBeanContext.run()
        def targetBean = ctx.getProxyTargetBean(ContextScopedInterceptedBean, null)
        def bean = ctx.getBean(ContextScopedInterceptedBean)


        then:
        bean instanceof Intercepted
        targetBean != bean
        ctx.getBeansOfType(ContextScopedInterceptedBean).size() == 1
        targetBean != null
        bean != null

        cleanup:
        ctx.close()
    }

    void "test getBeansOfType filters proxy targets with parallel beans"() {
        when:
        def ctx = DefaultBeanContext.run()
        Thread.sleep(100)
        def targetBean = ctx.getProxyTargetBean(ParallelBean, null)
        def bean = ctx.getBean(ParallelBean)


        then:
        bean instanceof Intercepted
        targetBean != bean
        ctx.getBeansOfType(ParallelBean).size() == 1
        targetBean != null
        bean != null

        cleanup:
        ctx.close()
    }

    void "test bean definitions are created for classes with only a qualifier"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test;

@javax.inject.Named("a")
class Bean {

}
''')
        then:
        beanDefinition != null
        !beanDefinition.isSingleton()
    }

    void "test abstract classes with only a qualifier do not generate bean definitions"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test;

@javax.inject.Named("a")
abstract class Bean {

}
''')
        then:
        beanDefinition == null
    }

    void "test classes with only AOP advice generate bean definitions"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test;

@io.micronaut.validation.Validated
class Bean {

}
''')
        then:
        beanDefinition != null
    }

    void "test abstract classes with only AOP advice do not generate bean definitions"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test;

@io.micronaut.validation.Validated
abstract class Bean {

}
''')
        then:
        beanDefinition == null
    }
}
