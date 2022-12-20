package io.micronaut.inject.configproperties.eachbeanparameter

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class EachBeanParameterSpec extends AbstractTypeElementSpec {

    void 'test interceptor on an event'() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['spec': 'EachBeanParameterSpec', 'mydatasources.default.xyz': '111', 'mydatasources.foo.xyz': '111', 'mydatasources.bar.xyz': '111'])

        when:
            def service = ctx.getBean(MyService)

        then:
            service.getDefaultBean().name == "default"
            service.getBarBean().name == "bar"
            service.getFooBean().name == "foo"

        cleanup:
            ctx.close()
    }
}
