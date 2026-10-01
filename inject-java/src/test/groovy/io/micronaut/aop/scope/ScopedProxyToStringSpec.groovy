package io.micronaut.aop.scope

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext

class ScopedProxyToStringSpec extends AbstractTypeElementSpec {

    void "refreshable bean delegates default toString to target"() {
        given:
        ApplicationContext context = buildContext("test.RefreshableBean", '''
package test;

import io.micronaut.runtime.context.scope.Refreshable;

@Refreshable
class RefreshableBean {
}
''')
        Class<?> beanType = context.classLoader.loadClass("test.RefreshableBean")

        when:
        Object bean = context.getBean(beanType)

        then:
        bean instanceof InterceptedProxy
        bean.toString().startsWith("test.RefreshableBean@")

        cleanup:
        context?.close()
    }

    void "refreshable bean can inherit non-final toString"() {
        given:
        ApplicationContext context = buildContext("test.RefreshableBean", '''
package test;

import io.micronaut.runtime.context.scope.Refreshable;

class ParentBean {
    @Override
    public String toString() {
        return "inherited-non-final";
    }
}

@Refreshable
class RefreshableBean extends ParentBean {
}
''')
        Class<?> beanType = context.classLoader.loadClass("test.RefreshableBean")

        when:
        Object bean = context.getBean(beanType)

        then:
        bean instanceof InterceptedProxy
        bean.toString() == "inherited-non-final"

        cleanup:
        context?.close()
    }

    void "refreshable bean can inherit final toString"() {
        given:
        ApplicationContext context = buildContext("test.RefreshableBean", '''
package test;

import io.micronaut.runtime.context.scope.Refreshable;

class ParentBean {
    @Override
    public final String toString() {
        return "inherited-final";
    }
}

@Refreshable
class RefreshableBean extends ParentBean {
}
''')
        Class<?> beanType = context.classLoader.loadClass("test.RefreshableBean")

        when:
        Object bean = context.getBean(beanType)

        then:
        bean instanceof InterceptedProxy
        bean.toString() == "inherited-final"

        cleanup:
        context?.close()
    }

    void "refreshable bean can override toString"() {
        given:
        ApplicationContext context = buildContext("test.RefreshableBean", '''
package test;

import io.micronaut.runtime.context.scope.Refreshable;

@Refreshable
class RefreshableBean {
    @Override
    public String toString() {
        return "refreshable";
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass("test.RefreshableBean")

        when:
        Object bean = context.getBean(beanType)

        then:
        bean instanceof InterceptedProxy
        bean.toString() == "refreshable"

        cleanup:
        context?.close()
    }
}
