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
package org.atinject.jakartatck.auto

import groovy.transform.PackageScope
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import junit.framework.TestCase
import org.atinject.jakartatck.auto.accessories.Cupholder
import org.atinject.jakartatck.auto.accessories.RoundThing
import org.atinject.jakartatck.auto.accessories.SpareTire
import spock.lang.PendingFeature
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

class Convertible implements Car {

    @Inject @Drivers Seat driversSeatA
    @Inject @Drivers Seat driversSeatB
    @Inject @PackageScope SpareTire spareTire
    @Inject @PackageScope Cupholder cupholder
    @Inject @PackageScope Provider<Engine> engineProvider

    private boolean methodWithZeroParamsInjected
    private boolean methodWithMultipleParamsInjected
    private boolean methodWithNonVoidReturnInjected

    private Seat constructorPlainSeat
    private Seat constructorDriversSeat
    private Tire constructorPlainTire
    private Tire constructorSpareTire
    private Provider<Seat> constructorPlainSeatProvider = nullProvider()
    private Provider<Seat> constructorDriversSeatProvider = nullProvider()
    private Provider<Tire> constructorPlainTireProvider = nullProvider()
    private Provider<Tire> constructorSpareTireProvider = nullProvider()

    @Inject protected Seat fieldPlainSeat
    @Inject @Drivers protected Seat fieldDriversSeat
    @Inject protected  Tire fieldPlainTire
    @Inject @Named("spare") protected  Tire fieldSpareTire
    @Inject protected Provider<Seat> fieldPlainSeatProvider = nullProvider()
    @Inject @Drivers protected  Provider<Seat> fieldDriversSeatProvider = nullProvider()
    @Inject protected Provider<Tire> fieldPlainTireProvider = nullProvider()
    @Inject @Named("spare") protected  Provider<Tire> fieldSpareTireProvider = nullProvider()

    private Seat methodPlainSeat
    private Seat methodDriversSeat
    private Tire methodPlainTire
    private Tire methodSpareTire
    private Provider<Seat> methodPlainSeatProvider = nullProvider()
    private Provider<Seat> methodDriversSeatProvider = nullProvider()
    private Provider<Tire> methodPlainTireProvider = nullProvider()
    private Provider<Tire> methodSpareTireProvider = nullProvider()

    @Inject @PackageScope static Seat staticFieldPlainSeat
    @Inject @PackageScope @Drivers static Seat staticFieldDriversSeat
    @Inject @PackageScope static Tire staticFieldPlainTire
    @Inject @PackageScope @Named("spare") static Tire staticFieldSpareTire
    @Inject @PackageScope static Provider<Seat> staticFieldPlainSeatProvider = nullProvider()
    @Inject @PackageScope @Drivers static Provider<Seat> staticFieldDriversSeatProvider = nullProvider()
    @Inject @PackageScope static Provider<Tire> staticFieldPlainTireProvider = nullProvider()
    @Inject @PackageScope @Named("spare") static Provider<Tire> staticFieldSpareTireProvider = nullProvider()

    private static Seat staticMethodPlainSeat
    private static Seat staticMethodDriversSeat
    private static Tire staticMethodPlainTire
    private static Tire staticMethodSpareTire
    private static Provider<Seat> staticMethodPlainSeatProvider = nullProvider()
    private static Provider<Seat> staticMethodDriversSeatProvider = nullProvider()
    private static Provider<Tire> staticMethodPlainTireProvider = nullProvider()
    private static Provider<Tire> staticMethodSpareTireProvider = nullProvider()

    @Inject @PackageScope Convertible(
            Seat plainSeat,
            @Drivers Seat driversSeat,
            Tire plainTire,
            @Named("spare") Tire spareTire,
            Provider<Seat> plainSeatProvider,
            @Drivers Provider<Seat> driversSeatProvider,
            Provider<Tire> plainTireProvider,
            @Named("spare") Provider<Tire> spareTireProvider) {
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

    @PackageScope void setSeat(Seat unused) {
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
            Seat plainSeat,
            @Drivers Seat driversSeat,
            Tire plainTire,
            @Named("spare") Tire spareTire,
            Provider<Seat> plainSeatProvider,
            @Drivers Provider<Seat> driversSeatProvider,
            Provider<Tire> plainTireProvider,
            @Named("spare") Provider<Tire> spareTireProvider) {
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
            Seat plainSeat,
            @Drivers Seat driversSeat,
            Tire plainTire,
            @Named("spare") Tire spareTire,
            Provider<Seat> plainSeatProvider,
            @Drivers Provider<Seat> driversSeatProvider,
            Provider<Tire> plainTireProvider,
            @Named("spare") Provider<Tire> spareTireProvider) {
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

    static class Tests extends Specification {

        private final BeanContext context = new DefaultBeanContext(){{
            start()
        }}
        private final Convertible car = context.getBean(Convertible)
        private final Cupholder cupholder = car.cupholder
        private final SpareTire spareTire = car.spareTire
        private final Tire plainTire = car.fieldPlainTire
        private final Engine engine = car.engineProvider.get()

        // smoke tests: if these fail all bets are off

        void testFieldsInjected() {
            expect:
            cupholder != null && spareTire != null
        }

        void testProviderReturnedValues() {
            expect:
            engine != null
        }

        // injecting different kinds of members

        void testMethodWithZeroParametersInjected() {
            expect:
            car.methodWithZeroParamsInjected
        }

        void testMethodWithMultipleParametersInjected() {
            expect:
            car.methodWithMultipleParamsInjected
        }

        void testNonVoidMethodInjected() {
            expect:
            car.methodWithNonVoidReturnInjected
        }

        void testPublicNoArgsConstructorInjected() {
            expect:
            engine.publicNoArgsConstructorInjected
        }

        void testSubtypeFieldsInjected() {
            expect:
            spareTire.hasSpareTireBeenFieldInjected()
        }

        void testSubtypeMethodsInjected() {
            expect:
            spareTire.hasSpareTireBeenMethodInjected()
        }

        void testSupertypeFieldsInjected() {
            expect:
            spareTire.hasTireBeenFieldInjected()
        }

        void testSupertypeMethodsInjected() {
            expect:
            spareTire.hasTireBeenMethodInjected()
        }

        void testTwiceOverriddenMethodInjectedWhenMiddleLacksAnnotation() {
            expect:
            engine.overriddenTwiceWithOmissionInMiddleInjected
        }

        // injected values

        @PendingFeature
        void testQualifiersNotInheritedFromOverriddenMethod() {
            expect:
            engine.overriddenMethodInjected
            !engine.qualifiersInheritedFromOverriddenMethod
        }

        void testConstructorInjectionWithValues() {
            expect:
            !(car.constructorPlainSeat instanceof DriversSeat)
            !(car.constructorPlainTire instanceof SpareTire)
            car.constructorDriversSeat instanceof DriversSeat
            car.constructorSpareTire instanceof SpareTire
        }

        void testFieldInjectionWithValues() {
            expect:
            !(car.fieldPlainSeat instanceof DriversSeat)
            !(car.fieldPlainTire instanceof SpareTire)
            car.fieldDriversSeat instanceof DriversSeat
            car.fieldSpareTire instanceof SpareTire
        }

        void testMethodInjectionWithValues() {
            expect:
            !(car.methodPlainSeat instanceof DriversSeat)
            !(car.methodPlainTire instanceof SpareTire)
            car.methodDriversSeat instanceof DriversSeat
            car.methodSpareTire instanceof SpareTire
        }

        // injected providers

        void testConstructorInjectionWithProviders() {
            expect:
            !(car.constructorPlainSeatProvider.get() instanceof DriversSeat)
            !(car.constructorPlainTireProvider.get() instanceof SpareTire)
            car.constructorDriversSeatProvider.get() instanceof DriversSeat
            car.constructorSpareTireProvider.get() instanceof SpareTire
        }

        void testFieldInjectionWithProviders() {
            expect:
            !(car.fieldPlainSeatProvider.get() instanceof DriversSeat)
            !(car.fieldPlainTireProvider.get() instanceof SpareTire)
            car.fieldDriversSeatProvider.get() instanceof DriversSeat
            car.fieldSpareTireProvider.get() instanceof SpareTire
        }

        void testMethodInjectionWithProviders() {
            expect:
            !(car.methodPlainSeatProvider.get() instanceof DriversSeat)
            !(car.methodPlainTireProvider.get() instanceof SpareTire)
            car.methodDriversSeatProvider.get() instanceof DriversSeat
            car.methodSpareTireProvider.get() instanceof SpareTire
        }


        // singletons

        void testConstructorInjectedProviderYieldsSingleton() {
            expect:
            car.constructorPlainSeatProvider.get().is(car.constructorPlainSeatProvider.get())
        }

        void testFieldInjectedProviderYieldsSingleton() {
            expect:
            car.fieldPlainSeatProvider.get().is(car.fieldPlainSeatProvider.get())
        }

        void testMethodInjectedProviderYieldsSingleton() {
            expect:
            car.methodPlainSeatProvider.get().is(car.methodPlainSeatProvider.get())
        }

        void testCircularlyDependentSingletons() {
            // uses provider.get() to get around circular deps
            expect:
            cupholder.seatProvider.get().getCupholder().is(cupholder)
        }


        // non singletons

        void testSingletonAnnotationNotInheritedFromSupertype() {
            expect:
            !car.driversSeatA.is(car.driversSeatB)
        }

        void testConstructorInjectedProviderYieldsDistinctValues() {
            expect:
            !car.constructorDriversSeatProvider.get().is(car.constructorDriversSeatProvider.get())
            !car.constructorPlainTireProvider.get().is(car.constructorPlainTireProvider.get())
            !car.constructorSpareTireProvider.get().is(car.constructorSpareTireProvider.get())
        }

        void testFieldInjectedProviderYieldsDistinctValues() {
            expect:
            !car.fieldDriversSeatProvider.get().is(car.fieldDriversSeatProvider.get())
            !car.fieldPlainTireProvider.get().is(car.fieldPlainTireProvider.get())
            !car.fieldSpareTireProvider.get().is(car.fieldSpareTireProvider.get())
        }

        void testMethodInjectedProviderYieldsDistinctValues() {
            expect:
            !car.methodDriversSeatProvider.get().is(car.methodDriversSeatProvider.get())
            !car.methodPlainTireProvider.get().is(car.methodPlainTireProvider.get())
            !car.methodSpareTireProvider.get().is(car.methodSpareTireProvider.get())
        }


        // mix inheritance + visibility

        void testPackagePrivateMethodInjectedDifferentPackages() {
            expect:
            spareTire.subPackagePrivateMethodInjected
            spareTire.superPackagePrivateMethodInjected
        }

        void testOverriddenProtectedMethodInjection() {
            expect:
            spareTire.subProtectedMethodInjected
            !spareTire.superProtectedMethodInjected
        }

        void testOverriddenPublicMethodNotInjected() {
            expect:
            spareTire.subPublicMethodInjected
            !spareTire.superPublicMethodInjected
        }


        // inject in order

        void testFieldsInjectedBeforeMethods() {
            expect:
            !spareTire.methodInjectedBeforeFields
        }

        void testSupertypeMethodsInjectedBeforeSubtypeFields() {
            expect:
            !spareTire.subtypeFieldInjectedBeforeSupertypeMethods
        }

        void testSupertypeMethodInjectedBeforeSubtypeMethods() {
            expect:
            !spareTire.subtypeMethodInjectedBeforeSupertypeMethods
        }


        // necessary injections occur

        void testPackagePrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            expect:
            spareTire.subPackagePrivateMethodForOverrideInjected
        }


        // override or similar method without @Inject

        void testPrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            expect:
            !spareTire.superPrivateMethodForOverrideInjected
        }

        void testPackagePrivateMethodNotInjectedWhenOverrideLacksAnnotation() {
            expect:
            !engine.subPackagePrivateMethodForOverrideInjected
            !engine.superPackagePrivateMethodForOverrideInjected
        }

        void testPackagePrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            expect:
            !spareTire.superPackagePrivateMethodForOverrideInjected
        }

        void testProtectedMethodNotInjectedWhenOverrideNotAnnotated() {
            expect:
            !spareTire.protectedMethodForOverrideInjected
        }

        void testPublicMethodNotInjectedWhenOverrideNotAnnotated() {
            expect:
            !spareTire.publicMethodForOverrideInjected
        }

        void testTwiceOverriddenMethodNotInjectedWhenOverrideLacksAnnotation() {
            expect:
            !engine.overriddenTwiceWithOmissionInSubclassInjected
        }

        void testOverriddingMixedWithPackagePrivate2() {
            expect:
            spareTire.spareTirePackagePrivateMethod2Injected
            ((Tire) spareTire).tirePackagePrivateMethod2Injected
            !((RoundThing) spareTire).roundThingPackagePrivateMethod2Injected
            plainTire.tirePackagePrivateMethod2Injected
            ((RoundThing) plainTire).roundThingPackagePrivateMethod2Injected
        }

        void testOverriddingMixedWithPackagePrivate3() {
            expect:
            !spareTire.spareTirePackagePrivateMethod3Injected
            ((Tire) spareTire).tirePackagePrivateMethod3Injected
            !((RoundThing) spareTire).roundThingPackagePrivateMethod3Injected
            plainTire.tirePackagePrivateMethod3Injected
            ((RoundThing) plainTire).roundThingPackagePrivateMethod3Injected
        }

        void testOverriddingMixedWithPackagePrivate4() {
            expect:
            !plainTire.tirePackagePrivateMethod4Injected
            ((RoundThing) plainTire).roundThingPackagePrivateMethod4Injected
        }

        // inject only once

        void testOverriddenPackagePrivateMethodInjectedOnlyOnce() {
            expect:
            !engine.@overriddenPackagePrivateMethodInjectedTwice
        }

        void testSimilarPackagePrivateMethodInjectedOnlyOnce() {
            expect:
            !spareTire.@similarPackagePrivateMethodInjectedTwice
        }

        void testOverriddenProtectedMethodInjectedOnlyOnce() {
            expect:
            !spareTire.@overriddenProtectedMethodInjectedTwice
        }

        void testOverriddenPublicMethodInjectedOnlyOnce() {
            expect:
            !spareTire.@overriddenPublicMethodInjectedTwice
        }

    }

    static class PrivateTests extends Specification {
        private final BeanContext context = new DefaultBeanContext().start()
        private final Convertible car = context.getBean(Convertible)
        private final Engine engine = car.engineProvider.get()
        private final SpareTire spareTire = car.spareTire

        void testSupertypePrivateMethodInjected() {
            expect:
            spareTire.superPrivateMethodInjected
            spareTire.subPrivateMethodInjected
        }

        void testPackagePrivateMethodInjectedSamePackage() {
            expect:
            engine.subPackagePrivateMethodInjected
            !engine.superPackagePrivateMethodInjected
        }

        void testPrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            expect:
            spareTire.subPrivateMethodForOverrideInjected
        }

        void testSimilarPrivateMethodInjectedOnlyOnce() {
            expect:
            !spareTire.similarPrivateMethodInjectedTwice
        }
    }
}
