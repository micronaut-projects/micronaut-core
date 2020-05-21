package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.annotation.ScopeOne
import spock.lang.Specification

class EagerInitStereotypesSpec extends Specification {

    void "test eager init stereotypes"() {
        when:
        def context = ApplicationContext.builder()
                .properties([ // tests validated config
                        'foo.bar.url':'http://localhost',
                        'foo.bar.name':'test'
                ])
                .eagerInitAnnotated(ScopeOne)
                .eagerInitConfiguration(true)
                .start()


        then:
        context.isRunning()
        EagerInitBean.created == true
        EagerConfig.created == true

        cleanup:
        context.close()

    }

}
