package io.micronaut.inject.beanbuilder;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class InterceptedTestBeanProducer {
    static int created;

    @TestProduces
    public LazyProducedBean lazyProducedBean() {
        created++;
        return new LazyProducedBean();
    }

    public static void reset() {
        created = 0;
    }

    public static class LazyProducedBean {
        public String ping() {
            return "pong";
        }
    }
}
