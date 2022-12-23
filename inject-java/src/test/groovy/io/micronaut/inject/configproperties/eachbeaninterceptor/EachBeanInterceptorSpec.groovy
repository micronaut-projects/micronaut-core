package io.micronaut.inject.configproperties.eachbeaninterceptor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class EachBeanInterceptorSpec extends AbstractTypeElementSpec {

    void 'test prototype interceptor is injected with introspected bean qualifier'() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['spec': 'EachBeanInterceptorSpec', 'mydatasources.default.xyz': '111', 'mydatasources.foo.xyz': '111', 'mydatasources.bar.xyz': '111'])

        when:
            def service = ctx.getBean(MyBean)

        then:
            service.getDefaultConnection().getCatalog() == "@Named('default')"
            service.getFooConnection().getCatalog() == "@Named('foo')"
            service.getBarConnection().getCatalog() == "@Named('bar')"

        cleanup:
            ctx.close()
    }
}
