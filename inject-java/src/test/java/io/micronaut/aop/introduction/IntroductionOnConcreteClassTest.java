package io.micronaut.aop.introduction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of IntroductionOnConcreteClassSpec.
 */
class IntroductionOnConcreteClassTest {

    @Test
    void testIntroductionOfNewInterfaceOnConcreteClass() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {

            ConcreteClass cc = applicationContext.getBean(ConcreteClass.class);
            ListenerAdviceInterceptor listenerAdviceInterceptor = applicationContext.getBean(ListenerAdviceInterceptor.class);

            StartupEvent event = new StartupEvent(applicationContext);
            @SuppressWarnings("unchecked")
            ApplicationEventListener<StartupEvent> listener = (ApplicationEventListener<StartupEvent>) assertInstanceOf(ApplicationEventListener.class, cc);
            listener.onApplicationEvent(event);

            assertTrue(listenerAdviceInterceptor.getReceivedMessages().contains(event));

            // cleanup state for other tests
            listenerAdviceInterceptor.getReceivedMessages().clear();
        }
    }
}
