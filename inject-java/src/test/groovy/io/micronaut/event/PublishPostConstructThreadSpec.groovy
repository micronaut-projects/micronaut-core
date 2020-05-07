package io.micronaut.event

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification


class PublishPostConstructThreadSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/3124')
    void 'test publishing an event on a different does not deadlock'() {
        given:
        DeadlockProducer producer = context.getBean(DeadlockProducer)

        expect:
        producer != null
    }
}
