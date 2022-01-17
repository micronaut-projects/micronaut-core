package io.micronaut.tracing.instrument.http

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification

class TracingExclusionsConfigurationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'tracing.exclusions[0]': '.*pattern.*',
            'tracing.exclusions[1]': '/literal',
    )

    TracingExclusionsConfiguration configuration = context.getBean(TracingExclusionsConfiguration)

    def "path #input is #desc by exclusion predicate"() {
        expect:
        configuration.exclusionTest().test(input) == expected

        where:
        input           | expected
        '/some/pattern' | true
        '/another'      | false
        '/literal'      | true
        '/literal/sub'  | false

        desc = expected ? 'excluded' : 'kept'
    }

    def "path #input is #desc by inclusion predicate"() {
        expect:
        configuration.inclusionTest().test(input) == expected

        where:
        input           | expected
        '/some/pattern' | false
        '/another'      | true
        '/literal'      | false
        '/literal/sub'  | true

        desc = expected ? 'included' : 'excluded'
    }
}
