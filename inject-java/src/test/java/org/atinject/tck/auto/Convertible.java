/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atinject.tck.auto;

import junit.framework.TestCase;
import org.atinject.tck.auto.accessories.Cupholder;
import org.atinject.tck.auto.accessories.RoundThing;
import org.atinject.tck.auto.accessories.SpareTire;
import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

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

    public static class Tests extends TestCase {

        private final BeanContext context = BeanContext.run();
        private final Convertible car = context.getBean(Convertible.class);
        private final Cupholder cupholder = car.cupholder;
        private final SpareTire spareTire = car.spareTire;
        private final Tire plainTire = car.fieldPlainTire;
        private final Engine engine = car.engineProvider.get();

        // smoke tests: if these fail all bets are off

        public void testFieldsInjected() {
            assertTrue(cupholder != null && spareTire != null);
        }

        public void testProviderReturnedValues() {
            assertNotNull(engine);
        }

        // injecting different kinds of members

        public void testMethodWithZeroParametersInjected() {
            assertTrue(car.methodWithZeroParamsInjected);
        }

        public void testMethodWithMultipleParametersInjected() {
            assertTrue(car.methodWithMultipleParamsInjected);
        }

        public void testNonVoidMethodInjected() {
            assertTrue(car.methodWithNonVoidReturnInjected);
        }

        public void testPublicNoArgsConstructorInjected() {
            assertTrue(engine.publicNoArgsConstructorInjected);
        }

        public void testSubtypeFieldsInjected() {
            assertTrue(spareTire.hasSpareTireBeenFieldInjected());
        }

        public void testSubtypeMethodsInjected() {
            assertTrue(spareTire.hasSpareTireBeenMethodInjected());
        }

        public void testSupertypeFieldsInjected() {
            assertTrue(spareTire.hasTireBeenFieldInjected());
        }

        public void testSupertypeMethodsInjected() {
            assertTrue(spareTire.hasTireBeenMethodInjected());
        }

        public void testTwiceOverriddenMethodInjectedWhenMiddleLacksAnnotation() {
            assertTrue(engine.overriddenTwiceWithOmissionInMiddleInjected);
        }

        // injected values

        public void testQualifiersNotInheritedFromOverriddenMethod() {
            assertFalse(engine.qualifiersInheritedFromOverriddenMethod);
        }

        public void testConstructorInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.constructorPlainSeat instanceof DriversSeat);
            assertFalse("Expected unqualified value",
                    car.constructorPlainTire instanceof SpareTire);
            assertTrue("Expected qualified value",
                    car.constructorDriversSeat instanceof DriversSeat);
            assertTrue("Expected qualified value",
                    car.constructorSpareTire instanceof SpareTire);
        }

        public void testFieldInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.fieldPlainSeat instanceof DriversSeat);
            assertFalse("Expected unqualified value",
                    car.fieldPlainTire instanceof SpareTire);
            assertTrue("Expected qualified value",
                    car.fieldDriversSeat instanceof DriversSeat);
            assertTrue("Expected qualified value",
                    car.fieldSpareTire instanceof SpareTire);
        }

        public void testMethodInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.methodPlainSeat instanceof DriversSeat);
            assertFalse("Expected unqualified value",
                    car.methodPlainTire instanceof SpareTire);
            assertTrue("Expected qualified value",
                    car.methodDriversSeat instanceof DriversSeat);
            assertTrue("Expected qualified value",
                    car.methodSpareTire instanceof SpareTire);
        }

        // injected providers

        public void testConstructorInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.constructorPlainSeatProvider.get() instanceof DriversSeat);
            assertFalse("Expected unqualified value",
                    car.constructorPlainTireProvider.get() instanceof SpareTire);
            assertTrue("Expected qualified value",
                    car.constructorDriversSeatProvider.get() instanceof DriversSeat);
            assertTrue("Expected qualified value",
                    car.constructorSpareTireProvider.get() instanceof SpareTire);
        }

        public void testFieldInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.fieldPlainSeatProvider.get() instanceof DriversSeat);
            assertFalse("Expected unqualified value",
                    car.fieldPlainTireProvider.get() instanceof SpareTire);
            assertTrue("Expected qualified value",
                    car.fieldDriversSeatProvider.get() instanceof DriversSeat);
            assertTrue("Expected qualified value",
                    car.fieldSpareTireProvider.get() instanceof SpareTire);
        }

        public void testMethodInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.methodPlainSeatProvider.get() instanceof DriversSeat);
            assertFalse("Expected unqualified value",
                    car.methodPlainTireProvider.get() instanceof SpareTire);
            assertTrue("Expected qualified value",
                    car.methodDriversSeatProvider.get() instanceof DriversSeat);
            assertTrue("Expected qualified value",
                    car.methodSpareTireProvider.get() instanceof SpareTire);
        }


        // singletons

        public void testConstructorInjectedProviderYieldsSingleton() {
            assertSame("Expected same value",
                    car.constructorPlainSeatProvider.get(), car.constructorPlainSeatProvider.get());
        }

        public void testFieldInjectedProviderYieldsSingleton() {
            assertSame("Expected same value",
                    car.fieldPlainSeatProvider.get(), car.fieldPlainSeatProvider.get());
        }

        public void testMethodInjectedProviderYieldsSingleton() {
            assertSame("Expected same value",
                    car.methodPlainSeatProvider.get(), car.methodPlainSeatProvider.get());
        }

        public void testCircularlyDependentSingletons() {
            // uses provider.get() to get around circular deps
            assertSame(cupholder.seatProvider.get().getCupholder(), cupholder);
        }


        // non singletons
        public void testSingletonAnnotationNotInheritedFromSupertype() {
            assertNotSame(car.driversSeatA, car.driversSeatB);
        }

        public void testConstructorInjectedProviderYieldsDistinctValues() {
            assertNotSame("Expected distinct values",
                    car.constructorDriversSeatProvider.get(), car.constructorDriversSeatProvider.get());
            assertNotSame("Expected distinct values",
                    car.constructorPlainTireProvider.get(), car.constructorPlainTireProvider.get());
            assertNotSame("Expected distinct values",
                    car.constructorSpareTireProvider.get(), car.constructorSpareTireProvider.get());
        }

        public void testFieldInjectedProviderYieldsDistinctValues() {
            assertNotSame("Expected distinct values",
                    car.fieldDriversSeatProvider.get(), car.fieldDriversSeatProvider.get());
            assertNotSame("Expected distinct values",
                    car.fieldPlainTireProvider.get(), car.fieldPlainTireProvider.get());
            assertNotSame("Expected distinct values",
                    car.fieldSpareTireProvider.get(), car.fieldSpareTireProvider.get());
        }

        public void testMethodInjectedProviderYieldsDistinctValues() {
            assertNotSame("Expected distinct values",
                    car.methodDriversSeatProvider.get(), car.methodDriversSeatProvider.get());
            assertNotSame("Expected distinct values",
                    car.methodPlainTireProvider.get(), car.methodPlainTireProvider.get());
            assertNotSame("Expected distinct values",
                    car.methodSpareTireProvider.get(), car.methodSpareTireProvider.get());
        }


        // mix inheritance + visibility

        public void testPackagePrivateMethodInjectedDifferentPackages() {
            assertTrue(spareTire.subPackagePrivateMethodInjected);
            assertTrue(spareTire.superPackagePrivateMethodInjected);
        }

        public void testOverriddenProtectedMethodInjection() {
            assertTrue(spareTire.subProtectedMethodInjected);
            assertFalse(spareTire.superProtectedMethodInjected);
        }

        public void testOverriddenPublicMethodNotInjected() {
            assertTrue(spareTire.subPublicMethodInjected);
            assertFalse(spareTire.superPublicMethodInjected);
        }


        // inject in order

        public void testFieldsInjectedBeforeMethods() {
            assertFalse(spareTire.methodInjectedBeforeFields);
        }

        public void testSupertypeMethodsInjectedBeforeSubtypeFields() {
            assertFalse(spareTire.subtypeFieldInjectedBeforeSupertypeMethods);
        }

        public void testSupertypeMethodInjectedBeforeSubtypeMethods() {
            assertFalse(spareTire.subtypeMethodInjectedBeforeSupertypeMethods);
        }


        // necessary injections occur

        public void testPackagePrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire.subPackagePrivateMethodForOverrideInjected);
        }


        // override or similar method without @Inject

        public void testPrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire.superPrivateMethodForOverrideInjected);
        }

        public void testPackagePrivateMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine.subPackagePrivateMethodForOverrideInjected);
            assertFalse(engine.superPackagePrivateMethodForOverrideInjected);
        }

        public void testPackagePrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire.superPackagePrivateMethodForOverrideInjected);
        }

        public void testProtectedMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire.protectedMethodForOverrideInjected);
        }

        public void testPublicMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire.publicMethodForOverrideInjected);
        }

        public void testTwiceOverriddenMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine.overriddenTwiceWithOmissionInSubclassInjected);
        }

        public void testOverriddingMixedWithPackagePrivate2() {
            assertTrue(spareTire.getSpareTirePackagePrivateMethod2Injected());
            assertTrue(((Tire) spareTire).tirePackagePrivateMethod2Injected);
            assertFalse(((RoundThing) spareTire).getRoundThingPackagePrivateMethod2Injected());

            assertTrue(plainTire.tirePackagePrivateMethod2Injected);
            assertTrue(((RoundThing) plainTire).getRoundThingPackagePrivateMethod2Injected());
        }

        public void testOverriddingMixedWithPackagePrivate3() {
            assertFalse(spareTire.getSpareTirePackagePrivateMethod3Injected());
            assertTrue(((Tire) spareTire).tirePackagePrivateMethod3Injected);
            assertFalse(((RoundThing) spareTire).getRoundThingPackagePrivateMethod3Injected());

            assertTrue(plainTire.tirePackagePrivateMethod3Injected);
            assertTrue(((RoundThing) plainTire).getRoundThingPackagePrivateMethod3Injected());
        }

        public void testOverriddingMixedWithPackagePrivate4() {
            assertFalse(plainTire.tirePackagePrivateMethod4Injected);
            assertTrue(((RoundThing) plainTire).getRoundThingPackagePrivateMethod4Injected());
        }

        // inject only once

        public void testOverriddenPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(engine.overriddenPackagePrivateMethodInjectedTwice);
        }

        public void testSimilarPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire.similarPackagePrivateMethodInjectedTwice);
        }

        public void testOverriddenProtectedMethodInjectedOnlyOnce() {
            assertFalse(spareTire.overriddenProtectedMethodInjectedTwice);
        }

        public void testOverriddenPublicMethodInjectedOnlyOnce() {
            assertFalse(spareTire.overriddenPublicMethodInjectedTwice);
        }

    }

    public static class PrivateTests extends TestCase {
        private final BeanContext context = new DefaultBeanContext().start();
        private final Convertible car = context.getBean(Convertible.class);
        private final Engine engine = car.engineProvider.get();
        private final SpareTire spareTire = car.spareTire;

        public void testSupertypePrivateMethodInjected() {
            assertTrue(spareTire.superPrivateMethodInjected);
            assertTrue(spareTire.subPrivateMethodInjected);
        }

        public void testPackagePrivateMethodInjectedSamePackage() {
            assertTrue(engine.subPackagePrivateMethodInjected);
            assertFalse(engine.superPackagePrivateMethodInjected);
        }

        public void testPrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire.subPrivateMethodForOverrideInjected);
        }

        public void testSimilarPrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire.similarPrivateMethodInjectedTwice);
        }
    }
}
