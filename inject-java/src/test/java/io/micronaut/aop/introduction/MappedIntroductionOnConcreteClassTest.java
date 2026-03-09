package io.micronaut.aop.introduction;

import io.micronaut.aop.Interceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of MappedIntroductionOnConcreteClassSpec.
 */
class MappedIntroductionOnConcreteClassTest extends AbstractTypeElementTest {



    @Test
    void testMappedIntroductionOfNewInterfaceOnConcreteClass() throws Exception {
        try (ApplicationContext applicationContext = buildContext("test.MyBeanWithMappedIntroduction", """
            package test;

            import jakarta.inject.Singleton;

            @io.micronaut.aop.introduction.ListenerAdviceMarker
            @Singleton
            public class MyBeanWithMappedIntroduction {
            }
            """)) {

        applicationContext.registerBeanDefinition(
            RuntimeBeanDefinition.builder(new ListenerAdviceInterceptor())
                .singleton(true)
                .exposedTypes(ListenerAdviceInterceptor.class, Interceptor.class)
                .build()
        );

        Class<?> beanClass = applicationContext.getClassLoader().loadClass("test.MyBeanWithMappedIntroduction");
        Object cc = applicationContext.getBean(beanClass);
        ListenerAdviceInterceptor listenerAdviceInterceptor = applicationContext.getBean(ListenerAdviceInterceptor.class);


        StartupEvent event = new StartupEvent(applicationContext);
        @SuppressWarnings("unchecked")
        ApplicationEventListener<StartupEvent> listener = (ApplicationEventListener<StartupEvent>) org.junit.jupiter.api.Assertions.assertInstanceOf(ApplicationEventListener.class, cc);
        listener.onApplicationEvent(event);

        assertTrue(listenerAdviceInterceptor.getReceivedMessages().contains(event));

        // cleanup state for other tests
        listenerAdviceInterceptor.getReceivedMessages().clear();
        }
    }
}
