/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atinject.javaxtck.auto;

import junit.framework.TestCase;
import org.atinject.javaxtck.auto.accessories.Cupholder;
import org.atinject.javaxtck.auto.accessories.RoundThing;
import org.atinject.javaxtck.auto.accessories.SpareTire;
import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import static org.junit.jupiter.api.Assertions.*;

public class Convertible implements Car {

    @Inject @Drivers Seat driversSeatA;
    @Inject @Drivers Seat driversSeatB;
    @Inject SpareTire spareTire;
    @Inject Cupholder cupholder;
    @Inject Provider<Engine> engineProvider;

    private boolean methodWithZeroParamsInjected;
    private boolean methodWithMultipleParamsInjected;
    private boolean methodWithNonVoidReturnInjected;

    private Seat constructorPlainSeat;
    private Seat constructorDriversSeat;
    private Tire constructorPlainTire;
    private Tire constructorSpareTire;
    private Provider<Seat> constructorPlainSeatProvider = nullProvider();
    private Provider<Seat> constructorDriversSeatProvider = nullProvider();
    private Provider<Tire> constructorPlainTireProvider = nullProvider();
    private Provider<Tire> constructorSpareTireProvider = nullProvider();

    @Inject protected Seat fieldPlainSeat;
    @Inject @Drivers protected Seat fieldDriversSeat;
    @Inject protected Tire fieldPlainTire;
    @Inject @Named("spare") protected Tire fieldSpareTire;
    @Inject protected Provider<Seat> fieldPlainSeatProvider = nullProvider();
    @Inject @Drivers protected Provider<Seat> fieldDriversSeatProvider = nullProvider();
    @Inject protected Provider<Tire> fieldPlainTireProvider = nullProvider();
    @Inject @Named("spare") protected Provider<Tire> fieldSpareTireProvider = nullProvider();

    private Seat methodPlainSeat;
    private Seat methodDriversSeat;
    private Tire methodPlainTire;
    private Tire methodSpareTire;
    private Provider<Seat> methodPlainSeatProvider = nullProvider();
    private Provider<Seat> methodDriversSeatProvider = nullProvider();
    private Provider<Tire> methodPlainTireProvider = nullProvider();
    private Provider<Tire> methodSpareTireProvider = nullProvider();

    @Inject static Seat staticFieldPlainSeat;
    @Inject @Drivers static Seat staticFieldDriversSeat;
    @Inject static Tire staticFieldPlainTire;
    @Inject @Named("spare") static Tire staticFieldSpareTire;
    @Inject static Provider<Seat> staticFieldPlainSeatProvider = nullProvider();
    @Inject @Drivers static Provider<Seat> staticFieldDriversSeatProvider = nullProvider();
    @Inject static Provider<Tire> staticFieldPlainTireProvider = nullProvider();
    @Inject @Named("spare") static Provider<Tire> staticFieldSpareTireProvider = nullProvider();

    private static Seat staticMethodPlainSeat;
    private static Seat staticMethodDriversSeat;
    private static Tire staticMethodPlainTire;
    private static Tire staticMethodSpareTire;
    private static Provider<Seat> staticMethodPlainSeatProvider = nullProvider();
    private static Provider<Seat> staticMethodDriversSeatProvider = nullProvider();
    private static Provider<Tire> staticMethodPlainTireProvider = nullProvider();
    private static Provider<Tire> staticMethodSpareTireProvider = nullProvider();

    @Inject Convertible(
            Seat plainSeat,
            @Drivers Seat driversSeat,
            Tire plainTire,
            @Named("spare") Tire spareTire,
            Provider<Seat> plainSeatProvider,
            @Drivers Provider<Seat> driversSeatProvider,
            Provider<Tire> plainTireProvider,
            @Named("spare") Provider<Tire> spareTireProvider) {
        constructorPlainSeat = plainSeat;
        constructorDriversSeat = driversSeat;
        constructorPlainTire = plainTire;
        constructorSpareTire = spareTire;
        constructorPlainSeatProvider = plainSeatProvider;
        constructorDriversSeatProvider = driversSeatProvider;
        constructorPlainTireProvider = plainTireProvider;
        constructorSpareTireProvider = spareTireProvider;
    }

    Convertible() {
        throw new AssertionError("Unexpected call to non-injectable constructor");
    }

    void setSeat(Seat unused) {
        throw new AssertionError("Unexpected call to non-injectable method");
    }

    @Inject void injectMethodWithZeroArgs() {
        methodWithZeroParamsInjected = true;
    }

    @Inject String injectMethodWithNonVoidReturn() {
        methodWithNonVoidReturnInjected = true;
        return "unused";
    }

    @Inject void injectInstanceMethodWithManyArgs(
            Seat plainSeat,
            @Drivers Seat driversSeat,
            Tire plainTire,
            @Named("spare") Tire spareTire,
            Provider<Seat> plainSeatProvider,
            @Drivers Provider<Seat> driversSeatProvider,
            Provider<Tire> plainTireProvider,
            @Named("spare") Provider<Tire> spareTireProvider) {
        methodWithMultipleParamsInjected = true;

        methodPlainSeat = plainSeat;
        methodDriversSeat = driversSeat;
        methodPlainTire = plainTire;
        methodSpareTire = spareTire;
        methodPlainSeatProvider = plainSeatProvider;
        methodDriversSeatProvider = driversSeatProvider;
        methodPlainTireProvider = plainTireProvider;
        methodSpareTireProvider = spareTireProvider;
    }

    @Inject static void injectStaticMethodWithManyArgs(
            Seat plainSeat,
            @Drivers Seat driversSeat,
            Tire plainTire,
            @Named("spare") Tire spareTire,
            Provider<Seat> plainSeatProvider,
            @Drivers Provider<Seat> driversSeatProvider,
            Provider<Tire> plainTireProvider,
            @Named("spare") Provider<Tire> spareTireProvider) {
        staticMethodPlainSeat = plainSeat;
        staticMethodDriversSeat = driversSeat;
        staticMethodPlainTire = plainTire;
        staticMethodSpareTire = spareTire;
        staticMethodPlainSeatProvider = plainSeatProvider;
        staticMethodDriversSeatProvider = driversSeatProvider;
        staticMethodPlainTireProvider = plainTireProvider;
        staticMethodSpareTireProvider = spareTireProvider;

    }

    /**
     * Returns a provider that always returns null. This is used as a default
     * value to avoid null checks for omitted provider injections.
     */
    private static <T> Provider<T> nullProvider() {
        return new NullProvider<T>();
    }

    static class NullProvider<T> implements Provider<T> {

        @Override
        public T get() {
            return null;
        }
    }

    public static ThreadLocal<Convertible> localConvertible = new ThreadLocal<Convertible>();


    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    public static class Tests {

        private final BeanContext context = BeanContext.run();
        private final Convertible car = context.getBean(Convertible.class);
        private final Cupholder cupholder = car.cupholder;
        private final SpareTire spareTire = car.spareTire;
        private final Tire plainTire = car.fieldPlainTire;
        private final Engine engine = car.engineProvider.get();

        // smoke tests: if these fail all bets are off

        @Test
        public void testFieldsInjected() {
            assertTrue(cupholder != null && spareTire != null);
        }

        @Test
        public void testProviderReturnedValues() {
            assertNotNull(engine);
        }

        // injecting different kinds of members

        @Test
        public void testMethodWithZeroParametersInjected() {
            assertTrue(car.methodWithZeroParamsInjected);
        }

        @Test
        public void testMethodWithMultipleParametersInjected() {
            assertTrue(car.methodWithMultipleParamsInjected);
        }

        @Test
        public void testNonVoidMethodInjected() {
            assertTrue(car.methodWithNonVoidReturnInjected);
        }

        @Test
        public void testPublicNoArgsConstructorInjected() {
            assertTrue(engine.publicNoArgsConstructorInjected);
        }

        @Test
        public void testSubtypeFieldsInjected() {
            assertTrue(spareTire.hasSpareTireBeenFieldInjected());
        }

        @Test
        public void testSubtypeMethodsInjected() {
            assertTrue(spareTire.hasSpareTireBeenMethodInjected());
        }

        @Test
        public void testSupertypeFieldsInjected() {
            assertTrue(spareTire.hasTireBeenFieldInjected());
        }

        @Test
        public void testSupertypeMethodsInjected() {
            assertTrue(spareTire.hasTireBeenMethodInjected());
        }

        @Test
        public void testTwiceOverriddenMethodInjectedWhenMiddleLacksAnnotation() {
            assertTrue(engine.overriddenTwiceWithOmissionInMiddleInjected);
        }

        // injected values

/*        @Test
        @Disabled
        public void testQualifiersNotInheritedFromOverriddenMethod() {
            assertTrue(engine.overriddenMethodInjected);
            assertFalse(engine.qualifiersInheritedFromOverriddenMethod);
        }*/

        @Test
        public void testConstructorInjectionWithValues() {
            assertFalse(car.constructorPlainSeat instanceof DriversSeat, "Expected unqualified value");
            assertFalse(car.constructorPlainTire instanceof SpareTire, "Expected unqualified value");
            assertTrue(car.constructorDriversSeat instanceof DriversSeat, "Expected qualified value");
            assertTrue(car.constructorSpareTire instanceof SpareTire, "Expected qualified value");
        }

        @Test
        public void testFieldInjectionWithValues() {
            assertFalse(car.fieldPlainSeat instanceof DriversSeat, "Expected unqualified value");
            assertFalse(car.fieldPlainTire instanceof SpareTire, "Expected unqualified value");
            assertTrue(car.fieldDriversSeat instanceof DriversSeat, "Expected qualified value");
            assertTrue(car.fieldSpareTire instanceof SpareTire, "Expected qualified value");
        }

        @Test
        public void testMethodInjectionWithValues() {
            assertFalse(car.methodPlainSeat instanceof DriversSeat, "Expected unqualified value");
            assertFalse(car.methodPlainTire instanceof SpareTire, "Expected unqualified value");
            assertTrue(car.methodDriversSeat instanceof DriversSeat, "Expected qualified value");
            assertTrue(car.methodSpareTire instanceof SpareTire, "Expected qualified value");
        }

        // injected providers

        @Test
        public void testConstructorInjectionWithProviders() {
            assertFalse(car.constructorPlainSeatProvider.get() instanceof DriversSeat, "Expected unqualified value");
            assertFalse(car.constructorPlainTireProvider.get() instanceof SpareTire, "Expected unqualified value");
            assertTrue(car.constructorDriversSeatProvider.get() instanceof DriversSeat, "Expected qualified value");
            assertTrue(car.constructorSpareTireProvider.get() instanceof SpareTire, "Expected qualified value");
        }

        @Test
        public void testFieldInjectionWithProviders() {
            assertFalse(car.fieldPlainSeatProvider.get() instanceof DriversSeat, "Expected unqualified value");
            assertFalse(car.fieldPlainTireProvider.get() instanceof SpareTire, "Expected unqualified value");
            assertTrue(car.fieldDriversSeatProvider.get() instanceof DriversSeat, "Expected qualified value");
            assertTrue(car.fieldSpareTireProvider.get() instanceof SpareTire, "Expected qualified value");
        }

        @Test
        public void testMethodInjectionWithProviders() {
            assertFalse(car.methodPlainSeatProvider.get() instanceof DriversSeat, "Expected unqualified value");
            assertFalse(car.methodPlainTireProvider.get() instanceof SpareTire, "Expected unqualified value");
            assertTrue(car.methodDriversSeatProvider.get() instanceof DriversSeat, "Expected qualified value");
            assertTrue(car.methodSpareTireProvider.get() instanceof SpareTire, "Expected qualified value");
        }


        // singletons

        @Test
        public void testConstructorInjectedProviderYieldsSingleton() {
            assertSame(car.constructorPlainSeatProvider.get(), car.constructorPlainSeatProvider.get(), "Expected same value");
        }

        @Test
        public void testFieldInjectedProviderYieldsSingleton() {
            assertSame(car.fieldPlainSeatProvider.get(), car.fieldPlainSeatProvider.get(), "Expected same value");
        }

        @Test
        public void testMethodInjectedProviderYieldsSingleton() {
            assertSame(car.methodPlainSeatProvider.get(), car.methodPlainSeatProvider.get(), "Expected same value");
        }

        @Test
        public void testCircularlyDependentSingletons() {
            // uses provider.get() to get around circular deps
            assertSame(cupholder.seatProvider.get().getCupholder(), cupholder);
        }


        // non singletons
        @Test
        public void testSingletonAnnotationNotInheritedFromSupertype() {
            assertNotSame(car.driversSeatA, car.driversSeatB);
        }

        @Test
        public void testConstructorInjectedProviderYieldsDistinctValues() {
            assertNotSame(car.constructorDriversSeatProvider.get(), car.constructorDriversSeatProvider.get(), "Expected distinct values");
            assertNotSame(car.constructorPlainTireProvider.get(), car.constructorPlainTireProvider.get(), "Expected distinct values");
            assertNotSame(car.constructorSpareTireProvider.get(), car.constructorSpareTireProvider.get(), "Expected distinct values");
        }

        @Test
        public void testFieldInjectedProviderYieldsDistinctValues() {
            assertNotSame(car.fieldDriversSeatProvider.get(), car.fieldDriversSeatProvider.get(), "Expected distinct values");
            assertNotSame(car.fieldPlainTireProvider.get(), car.fieldPlainTireProvider.get(), "Expected distinct values");
            assertNotSame(car.fieldSpareTireProvider.get(), car.fieldSpareTireProvider.get(), "Expected distinct values");
        }

        @Test
        public void testMethodInjectedProviderYieldsDistinctValues() {
            assertNotSame(car.methodDriversSeatProvider.get(), car.methodDriversSeatProvider.get(), "Expected distinct values");
            assertNotSame(car.methodPlainTireProvider.get(), car.methodPlainTireProvider.get(), "Expected distinct values");
            assertNotSame(car.methodSpareTireProvider.get(), car.methodSpareTireProvider.get(), "Expected distinct values");
        }


        // mix inheritance + visibility

        @Test
        public void testPackagePrivateMethodInjectedDifferentPackages() {
            assertTrue(spareTire.subPackagePrivateMethodInjected);
            assertTrue(spareTire.superPackagePrivateMethodInjected);
        }

        @Test
        public void testOverriddenProtectedMethodInjection() {
            assertTrue(spareTire.subProtectedMethodInjected);
            assertFalse(spareTire.superProtectedMethodInjected);
        }

        @Test
        public void testOverriddenPublicMethodNotInjected() {
            assertTrue(spareTire.subPublicMethodInjected);
            assertFalse(spareTire.superPublicMethodInjected);
        }


        // inject in order

        @Test
        public void testFieldsInjectedBeforeMethods() {
            assertFalse(spareTire.methodInjectedBeforeFields);
        }

        @Test
        public void testSupertypeMethodsInjectedBeforeSubtypeFields() {
            assertFalse(spareTire.subtypeFieldInjectedBeforeSupertypeMethods);
        }

        @Test
        public void testSupertypeMethodInjectedBeforeSubtypeMethods() {
            assertFalse(spareTire.subtypeMethodInjectedBeforeSupertypeMethods);
        }


        // necessary injections occur

        @Test
        public void testPackagePrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire.subPackagePrivateMethodForOverrideInjected);
        }


        // override or similar method without @Inject

        @Test
        public void testPrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire.superPrivateMethodForOverrideInjected);
        }

        @Test
        public void testPackagePrivateMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine.subPackagePrivateMethodForOverrideInjected);
            assertFalse(engine.superPackagePrivateMethodForOverrideInjected);
        }

        @Test
        public void testPackagePrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire.superPackagePrivateMethodForOverrideInjected);
        }

        @Test
        public void testProtectedMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire.protectedMethodForOverrideInjected);
        }

        @Test
        public void testPublicMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire.publicMethodForOverrideInjected);
        }

        @Test
        public void testTwiceOverriddenMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine.overriddenTwiceWithOmissionInSubclassInjected);
        }

        @Test
        public void testOverriddingMixedWithPackagePrivate2() {
            assertTrue(spareTire.packagePrivateMethod2Injected);
            assertTrue(((Tire) spareTire).packagePrivateMethod2Injected);
            assertFalse(((RoundThing) spareTire).packagePrivateMethod2Injected);

            assertTrue(plainTire.packagePrivateMethod2Injected);
            assertTrue(((RoundThing) plainTire).packagePrivateMethod2Injected);
        }

        @Test
        public void testOverriddingMixedWithPackagePrivate3() {
            assertFalse(spareTire.packagePrivateMethod3Injected);
            assertTrue(((Tire) spareTire).packagePrivateMethod3Injected);
            assertFalse(((RoundThing) spareTire).packagePrivateMethod3Injected);

            assertTrue(plainTire.packagePrivateMethod3Injected);
            assertTrue(((RoundThing) plainTire).packagePrivateMethod3Injected);
        }

        @Test
        public void testOverriddingMixedWithPackagePrivate4() {
            assertFalse(plainTire.packagePrivateMethod4Injected);
            assertTrue(((RoundThing) plainTire).packagePrivateMethod4Injected);
        }

        // inject only once

        @Test
        public void testOverriddenPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(engine.overriddenPackagePrivateMethodInjectedTwice);
        }

        @Test
        public void testSimilarPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire.similarPackagePrivateMethodInjectedTwice);
        }

        @Test
        public void testOverriddenProtectedMethodInjectedOnlyOnce() {
            assertFalse(spareTire.overriddenProtectedMethodInjectedTwice);
        }

        @Test
        public void testOverriddenPublicMethodInjectedOnlyOnce() {
            assertFalse(spareTire.overriddenPublicMethodInjectedTwice);
        }

    }

    public static class PrivateTests extends TestCase {
        private final BeanContext context = new DefaultBeanContext().start();
        private final Convertible car = context.getBean(Convertible.class);
        private final Engine engine = car.engineProvider.get();
        private final SpareTire spareTire = car.spareTire;

        @Test
        public void testSupertypePrivateMethodInjected() {
            assertTrue(spareTire.superPrivateMethodInjected);
            assertTrue(spareTire.subPrivateMethodInjected);
        }

        @Test
        public void testPackagePrivateMethodInjectedSamePackage() {
            assertTrue(engine.subPackagePrivateMethodInjected);
            assertFalse(engine.superPackagePrivateMethodInjected);
        }

        @Test
        public void testPrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire.subPrivateMethodForOverrideInjected);
        }

        @Test
        public void testSimilarPrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire.similarPrivateMethodInjectedTwice);
        }
    }
}
