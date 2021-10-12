package io.micronaut.runtime.event

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Test
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class EventListenerSpec {

    @Test
    void testImplementingAnInterfaceWithEventListener() {
        ApplicationContext ctx = ApplicationContext.run()
        EventListenerImpl impl = ctx.getBean(EventListenerImpl)

        assertFalse(impl.called)

        ctx.publishEvent(new MyEvent(""))

        assertTrue(impl.called)

        ctx.close()
    }
}
