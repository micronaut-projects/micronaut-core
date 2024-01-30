package io.micronaut.context

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.NoSuchBeanException
import jakarta.inject.Singleton
import spock.lang.Specification

class MultipleMatchingNoSuchBeanSpec extends Specification {

    def "multiple possible beans, but none matching failure message doesn't throw an exception"() {
        given:
        def ctx = ApplicationContext.run('spec.name': 'MultipleMatchingNoSuchBeanSpec')

        when:
        ctx.getBean(MyInterface)

        then:
        def ex = thrown(NoSuchBeanException)
        ex.message.contains '''* [MyInterface] requires the presence of a bean of type [io.micronaut.context.MultipleMatchingNoSuchBeanSpec$BeanBConfig].
                              | * [BeanBConfig] is disabled because:
                              |  - Required property [excluded] with value [so ignored] not present'''.stripMargin()
        ex.message.contains '''* [MyInterface] requires the presence of a bean of type [io.micronaut.context.MultipleMatchingNoSuchBeanSpec$BeanAConfig].
                              | * [BeanAConfig] is disabled because:
                              |  - Required property [excluded] with value [so ignored] not present'''.stripMargin()

        cleanup:
        ctx.close()
    }

    static interface MyInterface {}

    static class BeanA implements MyInterface {}

    static class BeanB implements MyInterface {}

    @Factory
    static class BeanAFactory {

        @EachBean(BeanAConfig)
        MyInterface make(BeanAConfig configuration) {
            return new BeanA();
        }
    }

    @Factory
    static class BeanBFactory {

        @EachBean(BeanBConfig)
        MyInterface make(BeanBConfig configuration) {
            return new BeanB();
        }
    }

    @Singleton
    @Requires(property = "excluded", value = "so ignored")
    static class BeanAConfig {}

    @Singleton
    @Requires(property = "excluded", value = "so ignored")
    static class BeanBConfig {}
}
