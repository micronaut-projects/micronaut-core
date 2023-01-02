package io.micronaut.inject.configproperties.eachbeanparameter

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.Qualifier
import io.micronaut.context.annotation.Primary
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.qualifiers.PrimaryQualifier
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
            Qualifier defaultNameQualifier = Qualifiers.byName("default")

        then:
            service.getDefaultBean().name == "default"
            service.getBarBean().name == "bar"
            service.getFooBean().name == "foo"

        and:
            ctx.getBeansOfType(AbstractDataSource).size() == datasourcesConfiguration.size() + 1 // DefaultDataSource
            ctx.getBeansOfType(MyHelper).size() == datasourcesConfiguration.size() + 1
            ctx.getBeansOfType(MyBean).size() == datasourcesConfiguration.size() + 1
            ctx.getBeansOfType(AbstractDataSource).stream().filter(it -> it instanceof MyDataSource).count() == 3
            ctx.getBeansOfType(AbstractDataSource).stream().filter(it -> it instanceof DefaultDataSource).count() == 1
            ctx.getBeansOfType(AbstractDataSource, PrimaryQualifier.INSTANCE).size() == 2
            ctx.getBeansOfType(MyHelper, PrimaryQualifier.INSTANCE).size() == 2
            ctx.getBeansOfType(MyBean, PrimaryQualifier.INSTANCE).size() == 2

            ctx.getBeansOfType(AbstractDataSource, defaultNameQualifier).size() == 2
            ctx.getBeansOfType(MyHelper, defaultNameQualifier).size() == 2
            ctx.getBeansOfType(MyBean, defaultNameQualifier).size() == 2

        when:
            ctx.getBean(AbstractDataSource, defaultNameQualifier)
        then:
            thrown(NonUniqueBeanException) // DefaultDataSource is annotated with `@Primary`. MyDataSource also defines primary via the annotation member `@EachProperty(value = "mydatasources", primary = "default")`

        when:
            ctx.getBean(MyHelper, defaultNameQualifier)
        then:
            noExceptionThrown() // should not throw thrown(NonUniqueBeanException)?

        when:
            ctx.getBean(MyBean, defaultNameQualifier)
        then:
            noExceptionThrown() // should not throw thrown(NonUniqueBeanException)?

        cleanup:
            ctx.close()
    }
}
