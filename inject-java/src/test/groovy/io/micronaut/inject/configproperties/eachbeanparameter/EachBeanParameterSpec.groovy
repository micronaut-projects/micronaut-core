package io.micronaut.inject.configproperties.eachbeanparameter

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers

class EachBeanParameterSpec extends AbstractTypeElementSpec {

    void 'test name parameter is properly injected when a bean is annotated with @Named and @Primary'() {
        given:
            Map<String, Object> datasourcesConfiguration = [
                    'mydatasources.default.xyz': '111',
                    'mydatasources.foo.xyz': '111',
                    'mydatasources.bar.xyz': '111'
            ]
            ApplicationContext ctx = ApplicationContext.run(['spec': 'EachBeanParameterSpec'] + datasourcesConfiguration)

        when:
            def service = ctx.getBean(MyService)

        then:
            service.getDefaultBean().name == "default"
            service.getBarBean().name == "bar"
            service.getFooBean().name == "foo"

        and:
            ctx.getBeansOfType(AbstractDataSource).size() == datasourcesConfiguration.size() + 1 // DefaultDataSource
            ctx.getBeansOfType(MyHelper).size() == datasourcesConfiguration.size() + 1
            ctx.getBeansOfType(MyBean).size() == datasourcesConfiguration.size() + 1
            ctx.getBeansOfType(MyBean, Qualifiers.byName("default")).size() == 2

        cleanup:
            ctx.close()
    }
}
