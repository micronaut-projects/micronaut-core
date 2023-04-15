package io.micronaut.inject.beanbuilder;

public class OtherBeanProducer {
    @TestProduces
    public BeanA beanB(String name) {
        return new BeanA(name);
    }

    public static class BeanA {
        private final String name;

        public BeanA(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}

