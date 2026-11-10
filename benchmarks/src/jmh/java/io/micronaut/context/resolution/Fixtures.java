package io.micronaut.context.resolution;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Beans for {@link BeanResolutionBenchmark}.
 */
final class Fixtures {
    static final String SPEC = "BeanResolutionBenchmark";

    private Fixtures() {
    }

    interface Service {
        int value();
    }

    interface Greeter {
        String greet();
    }

    interface Repo<T> {
        String kind();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class SingletonA implements Service {
        @Override
        public int value() {
            return 1;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class SingletonB {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class SingletonC {
    }

    @Singleton
    @Named("english")
    @Requires(property = "spec.name", value = SPEC)
    static class EnglishGreeter implements Greeter {
        @Override
        public String greet() {
            return "hello";
        }
    }

    @Singleton
    @Named("french")
    @Requires(property = "spec.name", value = SPEC)
    static class FrenchGreeter implements Greeter {
        @Override
        public String greet() {
            return "bonjour";
        }
    }

    @Singleton
    @Named("german")
    @Requires(property = "spec.name", value = SPEC)
    static class GermanGreeter implements Greeter {
        @Override
        public String greet() {
            return "hallo";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class StringRepo implements Repo<String> {
        @Override
        public String kind() {
            return "string";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class LongRepo implements Repo<Long> {
        @Override
        public String kind() {
            return "long";
        }
    }

    @Prototype
    @Requires(property = "spec.name", value = SPEC)
    static class PrototypeNoDeps {
    }

    @Prototype
    @Requires(property = "spec.name", value = SPEC)
    static class PrototypeWithDeps {
        final Service service;
        final SingletonB b;
        @Inject
        SingletonC c;
        Greeter greeter;

        PrototypeWithDeps(Service service, SingletonB b) {
            this.service = service;
            this.b = b;
        }

        @Inject
        void setGreeter(@Named("french") Greeter greeter) {
            this.greeter = greeter;
        }
    }

    @Prototype
    @Requires(property = "spec.name", value = SPEC)
    static class Chain1 {
        final Chain2 next;

        Chain1(Chain2 next) {
            this.next = next;
        }
    }

    @Prototype
    @Requires(property = "spec.name", value = SPEC)
    static class Chain2 {
        final Chain3 next;

        Chain2(Chain3 next) {
            this.next = next;
        }
    }

    @Prototype
    @Requires(property = "spec.name", value = SPEC)
    static class Chain3 {
        final Chain4 next;

        Chain3(Chain4 next) {
            this.next = next;
        }
    }

    @Prototype
    @Requires(property = "spec.name", value = SPEC)
    static class Chain4 {
        final PrototypeNoDeps next;

        Chain4(PrototypeNoDeps next) {
            this.next = next;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class ProviderHolder {
        final BeanProvider<PrototypeNoDeps> prototypes;
        final BeanProvider<Service> services;
        final jakarta.inject.Provider<Service> jakartaServices;

        ProviderHolder(BeanProvider<PrototypeNoDeps> prototypes, BeanProvider<Service> services, jakarta.inject.Provider<Service> jakartaServices) {
            this.prototypes = prototypes;
            this.services = services;
            this.jakartaServices = jakartaServices;
        }
    }

    interface Missing {
    }
}
