package io.micronaut.jackson.modules

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.beans.BeanIntrospection
import jakarta.inject.Singleton
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.time.LocalDateTime

@IgnoreIf({ !jvm.isJava14Compatible() })
class BeanIntrospectionModuleRecordSpec extends AbstractTypeElementSpec {
    def 'test record support'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;
import io.micronaut.core.annotation.Introspected;

@Introspected
record Test(String foo, String bar) {
}
''')
        def ctx = ApplicationContext.run(['spec.name': 'BeanIntrospectionModuleRecordSpec'])
        ctx.getBean(StaticBeanIntrospectionModule).introspectionMap[introspection.beanType] = introspection
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        def mapper = ctx.getBean(ObjectMapper)

        when:
        def value = mapper.readValue('{"foo":"1","bar":"2"}', introspection.beanType)
        then:
        value.foo == '1'
        value.bar == '2'

        where:
        ignoreReflectiveProperties << [true, false]
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/8330')
    def 'JsonFormat'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.annotation.Introspected;

@Introspected
record Test(@JsonFormat(pattern = "dd.MM.yyyy HH:mm:ss") LocalDateTime date) {
}
''')
        def ctx = ApplicationContext.run(['spec.name': 'BeanIntrospectionModuleRecordSpec'])
        ctx.getBean(StaticBeanIntrospectionModule).introspectionMap[introspection.beanType] = introspection
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        def mapper = ctx.getBean(ObjectMapper)

        when:
        def value = mapper.readValue('{"date":"13.11.2022 22:44:55"}', introspection.beanType)
        then:
        value.date == LocalDateTime.of(2022, 11, 13, 22, 44, 55)

        where:
        ignoreReflectiveProperties << [true, false]
    }

    @Singleton
    @Replaces(BeanIntrospectionModule)
    @Requires(property = "spec.name", value = 'BeanIntrospectionModuleRecordSpec')
    static class StaticBeanIntrospectionModule extends BeanIntrospectionModule {
        Map<Class<?>, BeanIntrospection> introspectionMap = [:]
        @Override
        protected BeanIntrospection<Object> findIntrospection(Class<?> beanClass) {
            return introspectionMap.get(beanClass)
        }
    }
}
