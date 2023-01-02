package io.micronaut.inject.configproperties.eachbeanreplaces

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class EachBeanReplacesSpec extends AbstractTypeElementSpec {

    void 'test each bean with empty bean doesnt replace itself'() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['spec': 'EachBeanReplacesSpec', 'mydatasources.default.xyz': '111', 'mydatasources.foo.xyz': '111', 'mydatasources.bar.xyz': '111'])

        when:
            def services = ctx.getBeansOfType(MyService)

        then:
            services.size() == 3

        cleanup:
            ctx.close()
    }
}
