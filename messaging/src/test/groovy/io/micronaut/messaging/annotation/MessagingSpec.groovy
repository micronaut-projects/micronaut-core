package io.micronaut.messaging.annotation


import io.micronaut.messaging.MessagingApplication
import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class MessagingSpec extends Specification {

    @Inject
    EmbeddedApplication embeddedApplication

    def 'embedded application is messaging'() {
        expect:
            embeddedApplication instanceof MessagingApplication
    }

    void "events are properly published"() {
        when:
            def eventCatcher = embeddedApplication.getApplicationContext().getBean(EventCatcher)
        then:
            eventCatcher.applicationStarted

        when:
            embeddedApplication.stop()
        then:
            eventCatcher.applicationStopped
            !embeddedApplication.getApplicationContext().isRunning()
    }

}
