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
package io.micronaut.jakarta.inject.tck

import groovy.transform.PackageScope
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import junit.framework.TestCase

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider

class Convertible implements io.micronaut.jakarta.inject.tck.Car {

    @Inject @io.micronaut.jakarta.inject.tck.Drivers io.micronaut.jakarta.inject.tck.Seat driversSeatA
    @Inject @io.micronaut.jakarta.inject.tck.Drivers io.micronaut.jakarta.inject.tck.Seat driversSeatB
    @Inject @PackageScope io.micronaut.jakarta.inject.tck.accessories.SpareTire spareTire
    @Inject @PackageScope io.micronaut.jakarta.inject.tck.accessories.Cupholder cupholder
    @Inject @PackageScope Provider<io.micronaut.jakarta.inject.tck.Engine> engineProvider

    private boolean methodWithZeroParamsInjected
    private boolean methodWithMultipleParamsInjected
    private boolean methodWithNonVoidReturnInjected

    private io.micronaut.jakarta.inject.tck.Seat constructorPlainSeat
    private io.micronaut.jakarta.inject.tck.Seat constructorDriversSeat
    private io.micronaut.jakarta.inject.tck.Tire constructorPlainTire
    private io.micronaut.jakarta.inject.tck.Tire constructorSpareTire
    private Provider<io.micronaut.jakarta.inject.tck.Seat> constructorPlainSeatProvider = nullProvider()
    private Provider<io.micronaut.jakarta.inject.tck.Seat> constructorDriversSeatProvider = nullProvider()
    private Provider<io.micronaut.jakarta.inject.tck.Tire> constructorPlainTireProvider = nullProvider()
    private Provider<io.micronaut.jakarta.inject.tck.Tire> constructorSpareTireProvider = nullProvider()

    @Inject protected io.micronaut.jakarta.inject.tck.Seat fieldPlainSeat
    @Inject @io.micronaut.jakarta.inject.tck.Drivers protected io.micronaut.jakarta.inject.tck.Seat fieldDriversSeat
    @Inject protected  io.micronaut.jakarta.inject.tck.Tire fieldPlainTire
    @Inject @Named("spare") protected  io.micronaut.jakarta.inject.tck.Tire fieldSpareTire
    @Inject protected Provider<io.micronaut.jakarta.inject.tck.Seat> fieldPlainSeatProvider = nullProvider()
    @Inject @io.micronaut.jakarta.inject.tck.Drivers protected  Provider<io.micronaut.jakarta.inject.tck.Seat> fieldDriversSeatProvider = nullProvider()
    @Inject protected Provider<io.micronaut.jakarta.inject.tck.Tire> fieldPlainTireProvider = nullProvider()
    @Inject @Named("spare") protected  Provider<io.micronaut.jakarta.inject.tck.Tire> fieldSpareTireProvider = nullProvider()

    private io.micronaut.jakarta.inject.tck.Seat methodPlainSeat
    private io.micronaut.jakarta.inject.tck.Seat methodDriversSeat
    private io.micronaut.jakarta.inject.tck.Tire methodPlainTire
    private io.micronaut.jakarta.inject.tck.Tire methodSpareTire
    private Provider<io.micronaut.jakarta.inject.tck.Seat> methodPlainSeatProvider = nullProvider()
    private Provider<io.micronaut.jakarta.inject.tck.Seat> methodDriversSeatProvider = nullProvider()
    private Provider<io.micronaut.jakarta.inject.tck.Tire> methodPlainTireProvider = nullProvider()
    private Provider<io.micronaut.jakarta.inject.tck.Tire> methodSpareTireProvider = nullProvider()

    @Inject @PackageScope static io.micronaut.jakarta.inject.tck.Seat staticFieldPlainSeat
    @Inject @PackageScope @io.micronaut.jakarta.inject.tck.Drivers static io.micronaut.jakarta.inject.tck.Seat staticFieldDriversSeat
    @Inject @PackageScope static io.micronaut.jakarta.inject.tck.Tire staticFieldPlainTire
    @Inject @PackageScope @Named("spare") static io.micronaut.jakarta.inject.tck.Tire staticFieldSpareTire
    @Inject @PackageScope static Provider<io.micronaut.jakarta.inject.tck.Seat> staticFieldPlainSeatProvider = nullProvider()
    @Inject @PackageScope @io.micronaut.jakarta.inject.tck.Drivers static Provider<io.micronaut.jakarta.inject.tck.Seat> staticFieldDriversSeatProvider = nullProvider()
    @Inject @PackageScope static Provider<io.micronaut.jakarta.inject.tck.Tire> staticFieldPlainTireProvider = nullProvider()
    @Inject @PackageScope @Named("spare") static Provider<io.micronaut.jakarta.inject.tck.Tire> staticFieldSpareTireProvider = nullProvider()

    private static io.micronaut.jakarta.inject.tck.Seat staticMethodPlainSeat
    private static io.micronaut.jakarta.inject.tck.Seat staticMethodDriversSeat
    private static io.micronaut.jakarta.inject.tck.Tire staticMethodPlainTire
    private static io.micronaut.jakarta.inject.tck.Tire staticMethodSpareTire
    private static Provider<io.micronaut.jakarta.inject.tck.Seat> staticMethodPlainSeatProvider = nullProvider()
    private static Provider<io.micronaut.jakarta.inject.tck.Seat> staticMethodDriversSeatProvider = nullProvider()
    private static Provider<io.micronaut.jakarta.inject.tck.Tire> staticMethodPlainTireProvider = nullProvider()
    private static Provider<io.micronaut.jakarta.inject.tck.Tire> staticMethodSpareTireProvider = nullProvider()

    @Inject @PackageScope Convertible(
            io.micronaut.jakarta.inject.tck.Seat plainSeat,
            @io.micronaut.jakarta.inject.tck.Drivers io.micronaut.jakarta.inject.tck.Seat driversSeat,
            io.micronaut.jakarta.inject.tck.Tire plainTire,
            @Named("spare") io.micronaut.jakarta.inject.tck.Tire spareTire,
            Provider<io.micronaut.jakarta.inject.tck.Seat> plainSeatProvider,
            @io.micronaut.jakarta.inject.tck.Drivers Provider<io.micronaut.jakarta.inject.tck.Seat> driversSeatProvider,
            Provider<io.micronaut.jakarta.inject.tck.Tire> plainTireProvider,
            @Named("spare") Provider<io.micronaut.jakarta.inject.tck.Tire> spareTireProvider) {
        constructorPlainSeat = plainSeat
        constructorDriversSeat = driversSeat
        constructorPlainTire = plainTire
        constructorSpareTire = spareTire
        constructorPlainSeatProvider = plainSeatProvider
        constructorDriversSeatProvider = driversSeatProvider
        constructorPlainTireProvider = plainTireProvider
        constructorSpareTireProvider = spareTireProvider
    }

    @PackageScope Convertible() {
        throw new AssertionError("Unexpected call to non-injectable constructor")
    }

    @PackageScope void setSeat(io.micronaut.jakarta.inject.tck.Seat unused) {
        throw new AssertionError("Unexpected call to non-injectable method")
    }

    @Inject @PackageScope void injectMethodWithZeroArgs() {
        methodWithZeroParamsInjected = true
    }

    @Inject @PackageScope String injectMethodWithNonVoidReturn() {
        methodWithNonVoidReturnInjected = true
        return "unused"
    }

    @Inject @PackageScope void injectInstanceMethodWithManyArgs(
            io.micronaut.jakarta.inject.tck.Seat plainSeat,
            @io.micronaut.jakarta.inject.tck.Drivers io.micronaut.jakarta.inject.tck.Seat driversSeat,
            io.micronaut.jakarta.inject.tck.Tire plainTire,
            @Named("spare") io.micronaut.jakarta.inject.tck.Tire spareTire,
            Provider<io.micronaut.jakarta.inject.tck.Seat> plainSeatProvider,
            @io.micronaut.jakarta.inject.tck.Drivers Provider<io.micronaut.jakarta.inject.tck.Seat> driversSeatProvider,
            Provider<io.micronaut.jakarta.inject.tck.Tire> plainTireProvider,
            @Named("spare") Provider<io.micronaut.jakarta.inject.tck.Tire> spareTireProvider) {
        methodWithMultipleParamsInjected = true

        methodPlainSeat = plainSeat
        methodDriversSeat = driversSeat
        methodPlainTire = plainTire
        methodSpareTire = spareTire
        methodPlainSeatProvider = plainSeatProvider
        methodDriversSeatProvider = driversSeatProvider
        methodPlainTireProvider = plainTireProvider
        methodSpareTireProvider = spareTireProvider
    }

    @Inject @PackageScope static void injectStaticMethodWithManyArgs(
            io.micronaut.jakarta.inject.tck.Seat plainSeat,
            @io.micronaut.jakarta.inject.tck.Drivers io.micronaut.jakarta.inject.tck.Seat driversSeat,
            io.micronaut.jakarta.inject.tck.Tire plainTire,
            @Named("spare") io.micronaut.jakarta.inject.tck.Tire spareTire,
            Provider<io.micronaut.jakarta.inject.tck.Seat> plainSeatProvider,
            @io.micronaut.jakarta.inject.tck.Drivers Provider<io.micronaut.jakarta.inject.tck.Seat> driversSeatProvider,
            Provider<io.micronaut.jakarta.inject.tck.Tire> plainTireProvider,
            @Named("spare") Provider<io.micronaut.jakarta.inject.tck.Tire> spareTireProvider) {
        staticMethodPlainSeat = plainSeat
        staticMethodDriversSeat = driversSeat
        staticMethodPlainTire = plainTire
        staticMethodSpareTire = spareTire
        staticMethodPlainSeatProvider = plainSeatProvider
        staticMethodDriversSeatProvider = driversSeatProvider
        staticMethodPlainTireProvider = plainTireProvider
        staticMethodSpareTireProvider = spareTireProvider
    }

    /**
     * Returns a provider that always returns null. This is used as a default
     * value to avoid null checks for omitted provider injections.
     */
    private static <T> Provider<T> nullProvider() {
        return new NullProvider<T>()
    }

    static class NullProvider<T> implements Provider<T> {

        @Override
        T get() {
            return null
        }
    }

    public static ThreadLocal<Convertible> localConvertible = new ThreadLocal<Convertible>()

    static class Tests extends TestCase {

        private final BeanContext context = new DefaultBeanContext(){{
            start()
        }}
        private final Convertible car = context.getBean(Convertible)
        private final io.micronaut.jakarta.inject.tck.accessories.Cupholder cupholder = car.cupholder
        private final io.micronaut.jakarta.inject.tck.accessories.SpareTire spareTire = car.spareTire
        private final io.micronaut.jakarta.inject.tck.Tire plainTire = car.fieldPlainTire
        private final io.micronaut.jakarta.inject.tck.Engine engine = car.engineProvider.get()

        // smoke tests: if these fail all bets are off

        void testFieldsInjected() {
            assertTrue(cupholder != null && spareTire != null)
        }

        void testProviderReturnedValues() {
            assertTrue(engine != null)
        }

        // injecting different kinds of members

        void testMethodWithZeroParametersInjected() {
            assertTrue(car.methodWithZeroParamsInjected)
        }

        void testMethodWithMultipleParametersInjected() {
            assertTrue(car.methodWithMultipleParamsInjected)
        }

        void testNonVoidMethodInjected() {
            assertTrue(car.methodWithNonVoidReturnInjected)
        }

        void testPublicNoArgsConstructorInjected() {
            assertTrue(engine.publicNoArgsConstructorInjected)
        }

        void testSubtypeFieldsInjected() {
            assertTrue(spareTire.hasSpareTireBeenFieldInjected())
        }

        void testSubtypeMethodsInjected() {
            assertTrue(spareTire.hasSpareTireBeenMethodInjected())
        }

        void testSupertypeFieldsInjected() {
            assertTrue(spareTire.hasTireBeenFieldInjected())
        }

        void testSupertypeMethodsInjected() {
            assertTrue(spareTire.hasTireBeenMethodInjected())
        }

        void testTwiceOverriddenMethodInjectedWhenMiddleLacksAnnotation() {
            assertTrue(engine.overriddenTwiceWithOmissionInMiddleInjected)
        }

        // injected values

        void testQualifiersNotInheritedFromOverriddenMethod() {
            assertFalse(engine.qualifiersInheritedFromOverriddenMethod)
        }

        void testConstructorInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.constructorPlainSeat instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertFalse("Expected unqualified value",
                    car.constructorPlainTire instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
            assertTrue("Expected qualified value",
                    car.constructorDriversSeat instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertTrue("Expected qualified value",
                    car.constructorSpareTire instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
        }

        void testFieldInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.fieldPlainSeat instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertFalse("Expected unqualified value",
                    car.fieldPlainTire instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
            assertTrue("Expected qualified value",
                    car.fieldDriversSeat instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertTrue("Expected qualified value",
                    car.fieldSpareTire instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
        }

        void testMethodInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.methodPlainSeat instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertFalse("Expected unqualified value",
                    car.methodPlainTire instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
            assertTrue("Expected qualified value",
                    car.methodDriversSeat instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertTrue("Expected qualified value",
                    car.methodSpareTire instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
        }

        // injected providers

        void testConstructorInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.constructorPlainSeatProvider.get() instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertFalse("Expected unqualified value",
                    car.constructorPlainTireProvider.get() instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
            assertTrue("Expected qualified value",
                    car.constructorDriversSeatProvider.get() instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertTrue("Expected qualified value",
                    car.constructorSpareTireProvider.get() instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
        }

        void testFieldInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.fieldPlainSeatProvider.get() instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertFalse("Expected unqualified value",
                    car.fieldPlainTireProvider.get() instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
            assertTrue("Expected qualified value",
                    car.fieldDriversSeatProvider.get() instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertTrue("Expected qualified value",
                    car.fieldSpareTireProvider.get() instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
        }

        void testMethodInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.methodPlainSeatProvider.get() instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertFalse("Expected unqualified value",
                    car.methodPlainTireProvider.get() instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
            assertTrue("Expected qualified value",
                    car.methodDriversSeatProvider.get() instanceof io.micronaut.jakarta.inject.tck.DriversSeat)
            assertTrue("Expected qualified value",
                    car.methodSpareTireProvider.get() instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
        }


        // singletons

        void testConstructorInjectedProviderYieldsSingleton() {
            assertSame("Expected same value",
                    car.constructorPlainSeatProvider.get(), car.constructorPlainSeatProvider.get())
        }

        void testFieldInjectedProviderYieldsSingleton() {
            assertSame("Expected same value",
                    car.fieldPlainSeatProvider.get(), car.fieldPlainSeatProvider.get())
        }

        void testMethodInjectedProviderYieldsSingleton() {
            assertSame("Expected same value",
                    car.methodPlainSeatProvider.get(), car.methodPlainSeatProvider.get())
        }

        void testCircularlyDependentSingletons() {
            // uses provider.get() to get around circular deps
            assertSame(cupholder.seatProvider.get().getCupholder(), cupholder)
        }


        // non singletons

        void testSingletonAnnotationNotInheritedFromSupertype() {
            assertNotSame(car.driversSeatA, car.driversSeatB)
        }

        void testConstructorInjectedProviderYieldsDistinctValues() {
            assertNotSame("Expected distinct values",
                    car.constructorDriversSeatProvider.get(), car.constructorDriversSeatProvider.get())
            assertNotSame("Expected distinct values",
                    car.constructorPlainTireProvider.get(), car.constructorPlainTireProvider.get())
            assertNotSame("Expected distinct values",
                    car.constructorSpareTireProvider.get(), car.constructorSpareTireProvider.get())
        }

        void testFieldInjectedProviderYieldsDistinctValues() {
            assertNotSame("Expected distinct values",
                    car.fieldDriversSeatProvider.get(), car.fieldDriversSeatProvider.get())
            assertNotSame("Expected distinct values",
                    car.fieldPlainTireProvider.get(), car.fieldPlainTireProvider.get())
            assertNotSame("Expected distinct values",
                    car.fieldSpareTireProvider.get(), car.fieldSpareTireProvider.get())
        }

        void testMethodInjectedProviderYieldsDistinctValues() {
            assertNotSame("Expected distinct values",
                    car.methodDriversSeatProvider.get(), car.methodDriversSeatProvider.get())
            assertNotSame("Expected distinct values",
                    car.methodPlainTireProvider.get(), car.methodPlainTireProvider.get())
            assertNotSame("Expected distinct values",
                    car.methodSpareTireProvider.get(), car.methodSpareTireProvider.get())
        }


        // mix inheritance + visibility

        void testPackagePrivateMethodInjectedDifferentPackages() {
            assertTrue(spareTire.subPackagePrivateMethodInjected)
            assertTrue(spareTire.superPackagePrivateMethodInjected)
        }

        void testOverriddenProtectedMethodInjection() {
            assertTrue(spareTire.subProtectedMethodInjected)
            assertFalse(spareTire.superProtectedMethodInjected)
        }

        void testOverriddenPublicMethodNotInjected() {
            assertTrue(spareTire.subPublicMethodInjected)
            assertFalse(spareTire.superPublicMethodInjected)
        }


        // inject in order

        void testFieldsInjectedBeforeMethods() {
            assertFalse(spareTire.methodInjectedBeforeFields)
        }

        void testSupertypeMethodsInjectedBeforeSubtypeFields() {
            assertFalse(spareTire.subtypeFieldInjectedBeforeSupertypeMethods)
        }

        void testSupertypeMethodInjectedBeforeSubtypeMethods() {
            assertFalse(spareTire.subtypeMethodInjectedBeforeSupertypeMethods)
        }


        // necessary injections occur

        void testPackagePrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire.subPackagePrivateMethodForOverrideInjected)
        }


        // override or similar method without @Inject

        void testPrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire.superPrivateMethodForOverrideInjected)
        }

        void testPackagePrivateMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine.subPackagePrivateMethodForOverrideInjected)
            assertFalse(engine.superPackagePrivateMethodForOverrideInjected)
        }

        void testPackagePrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire.superPackagePrivateMethodForOverrideInjected)
        }

        void testProtectedMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire.protectedMethodForOverrideInjected)
        }

        void testPublicMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire.publicMethodForOverrideInjected)
        }

        void testTwiceOverriddenMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine.overriddenTwiceWithOmissionInSubclassInjected)
        }

        void testOverriddingMixedWithPackagePrivate2() {
            assertTrue(spareTire.spareTirePackagePrivateMethod2Injected)
            assertTrue(((io.micronaut.jakarta.inject.tck.Tire) spareTire).tirePackagePrivateMethod2Injected)
            assertFalse(((io.micronaut.jakarta.inject.tck.accessories.RoundThing) spareTire).roundThingPackagePrivateMethod2Injected)

            assertTrue(plainTire.tirePackagePrivateMethod2Injected)
            assertTrue(((io.micronaut.jakarta.inject.tck.accessories.RoundThing) plainTire).roundThingPackagePrivateMethod2Injected)
        }

        void testOverriddingMixedWithPackagePrivate3() {
            assertFalse(spareTire.spareTirePackagePrivateMethod3Injected)
            assertTrue(((io.micronaut.jakarta.inject.tck.Tire) spareTire).tirePackagePrivateMethod3Injected)
            assertFalse(((io.micronaut.jakarta.inject.tck.accessories.RoundThing) spareTire).roundThingPackagePrivateMethod3Injected)

            assertTrue(plainTire.tirePackagePrivateMethod3Injected)
            assertTrue(((io.micronaut.jakarta.inject.tck.accessories.RoundThing) plainTire).roundThingPackagePrivateMethod3Injected)
        }

        void testOverriddingMixedWithPackagePrivate4() {
            assertFalse(plainTire.tirePackagePrivateMethod4Injected)
            assertTrue(((io.micronaut.jakarta.inject.tck.accessories.RoundThing) plainTire).roundThingPackagePrivateMethod4Injected)
        }

        // inject only once

        void testOverriddenPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(engine.@overriddenPackagePrivateMethodInjectedTwice)
        }

        void testSimilarPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire.@similarPackagePrivateMethodInjectedTwice)
        }

        void testOverriddenProtectedMethodInjectedOnlyOnce() {
            assertFalse(spareTire.@overriddenProtectedMethodInjectedTwice)
        }

        void testOverriddenPublicMethodInjectedOnlyOnce() {
            assertFalse(spareTire.@overriddenPublicMethodInjectedTwice)
        }

    }



    static class PrivateTests extends TestCase {
        private final BeanContext context = new DefaultBeanContext().start()
        private final Convertible car = context.getBean(Convertible)
        private final io.micronaut.jakarta.inject.tck.Engine engine = car.engineProvider.get()
        private final io.micronaut.jakarta.inject.tck.accessories.SpareTire spareTire = car.spareTire

        void testSupertypePrivateMethodInjected() {
            assertTrue(spareTire.superPrivateMethodInjected)
            assertTrue(spareTire.subPrivateMethodInjected)
        }

        void testPackagePrivateMethodInjectedSamePackage() {
            assertTrue(engine.subPackagePrivateMethodInjected)
            assertFalse(engine.superPackagePrivateMethodInjected)
        }

        void testPrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire.subPrivateMethodForOverrideInjected)
        }

        void testSimilarPrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire.similarPrivateMethodInjectedTwice)
        }
    }
}
