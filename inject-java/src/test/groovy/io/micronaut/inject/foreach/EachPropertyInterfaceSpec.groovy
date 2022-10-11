package io.micronaut.inject.foreach

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EachPropertyInterfaceSpec extends Specification {

    void "test @EachProperty on interface"() {
        given:
        def context = ApplicationContext.run(
                "foo.bar.one.host": 'host1',
                "foo.bar.one.port": '9999',
                "foo.bar.two.host": 'host2',
                "foo.bar.two.port": '8888'
        )

        when:
        def one = context.getBean(InterfaceConfig, Qualifiers.byName("one"))
        def two = context.getBean(InterfaceConfig, Qualifiers.byName("two"))

        then:
        one.host == 'host1'
        one.port == 9999
        two.host == 'host2'
        two.port == 8888

        cleanup:
        context.close()
    }

    void "test @EachProperty on interface with nested @ConfigurationProperties"() {
        given:
        def context = ApplicationContext.run(
                "foo.bar[0].address.host": 'host1',
                "foo.bar[0].address.port": '9999',
                "foo.bar[1].address.host": 'host2',
                "foo.bar[1].address.port": '8888'
        )

        when:
        def one = context.getBean(InterfaceAggregatorConfig, Qualifiers.byName("0"))
        def two = context.getBean(InterfaceAggregatorConfig, Qualifiers.byName("1"))

        then:
        one.address.host == 'host1'
        one.address.port == 9999
        two.address.host == 'host2'
        two.address.port == 8888

        cleanup:
        context.close()
    }
}
