package io.micronaut.kotlin.processing.aop.compile


import io.micronaut.context.ApplicationContext
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext
import static io.micronaut.annotation.processing.test.KotlinCompiler.getBeanDefinition

class RequestBeanScope extends Specification {

    void 'test request scope bean with properties'() {
        when:
            ApplicationContext applicationContext = buildContext('''
package test

import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Inject

@RequestScope
open class RequestScopeClass (
    open var text: String?,
    open val list: MutableList<String> = mutableListOf()
) {
    @Inject
    constructor() : this(null)
}
''')
        def beanDefinition = getBeanDefinition(applicationContext, 'test.$RequestScopeClass$Definition' + BeanDefinitionVisitor.PROXY_SUFFIX)
        def bean = ((InstantiatableBeanDefinition) beanDefinition).instantiate(applicationContext)
        def methods = bean.class.declaredMethods.toList().findAll { !it.isSynthetic() }.collect { it.getName() }.sort()

        then:
        methods == ["\$withBeanQualifier", "getList", "getText", "interceptedTarget", "setText"] as List

        cleanup:
        applicationContext.close()
    }

}
