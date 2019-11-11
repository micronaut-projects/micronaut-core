package io.micronaut.session

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.session.event.AbstractSessionEvent
import io.micronaut.session.event.SessionCreatedEvent
import io.micronaut.session.event.SessionDeletedEvent
import io.micronaut.session.event.SessionExpiredEvent
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class InMemorySessionStoreSpec extends Specification {

    void "test in-memory session store read and write"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run()
        SessionStore sessionStore = applicationContext.getBean(SessionStore)
        TestListener listener = applicationContext.getBean(TestListener)
        Session session = sessionStore.newSession()

        session.put("foo", "bar")

        then:
        session != null
        session.id
        !session.expired
        session.creationTime
        session.lastAccessedTime

        when:
        sessionStore.save(session).get()
        def lastAccessedTime = session.lastAccessedTime

        then:
        listener.events.size() == 1
        listener.events[0] instanceof SessionCreatedEvent

        when:
        Thread.sleep(50)
        session == sessionStore.findSession(session.id).get().get()
        def conditions = new PollingConditions(timeout: 10)

        then:
        conditions.eventually {
            session.lastAccessedTime > lastAccessedTime
            session.get("foo").isPresent()
            session.get("foo").get() == "bar"
        }

        when:
        listener.events.clear()
        sessionStore.deleteSession(session.id)

        then:
        conditions.eventually {
            assert listener.events.size() == 1
            assert listener.events[0] instanceof SessionDeletedEvent
            assert !sessionStore.findSession(session.id).get().isPresent()
        }

        cleanup:
        applicationContext.close()
    }

    void "test session expiry"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(['micronaut.session.max-inactive-interval': 'PT1S'])
        SessionStore sessionStore = applicationContext.getBean(SessionStore)
        TestListener listener = applicationContext.getBean(TestListener)
        Session session = sessionStore.newSession()
        session.put("foo", "bar")
        sessionStore.save(session)
        String id = session.id
        PollingConditions conditions = new PollingConditions(timeout: 5, initialDelay: 2)

        then:
        conditions.eventually {
            assert !sessionStore.findSession(id).get().isPresent()
            assert listener.events.any { it instanceof SessionExpiredEvent }
        }

        cleanup:
        applicationContext.close()
    }

    void "test session prompt expiration"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.session.prompt-expiration': true,
                'micronaut.session.max-inactive-interval': 'PT3S'
        ])
        SessionStore sessionStore = applicationContext.getBean(SessionStore)
        TestListener listener = applicationContext.getBean(TestListener)
        Session session = sessionStore.newSession()

        session.put("foo", "bar")
        sessionStore.save(session)

        then:
        session != null
        session.id
        !session.expired
        session.creationTime
        session.lastAccessedTime

        when:
        sessionStore.save(session).get()
        def lastAccessedTime = session.lastAccessedTime

        then:
        listener.events.size() == 1
        listener.events[0] instanceof SessionCreatedEvent

        when:
        Thread.sleep(50)
        session == sessionStore.findSession(session.id).get().get()
        def conditions = new PollingConditions(timeout: 10)

        then:
        conditions.eventually {
            assert session.lastAccessedTime > lastAccessedTime
            assert session.get("foo").isPresent()
            assert session.get("foo").get() == "bar"
            assert listener.events.any { it instanceof SessionExpiredEvent }
        }

        cleanup:
        applicationContext.close()
    }

    @Singleton
    static class TestListener implements ApplicationEventListener<AbstractSessionEvent> {
        List<AbstractSessionEvent> events = []
        @Override
        void onApplicationEvent(AbstractSessionEvent event) {
            events.add(event)
        }
    }
}
