package io.micronaut.javax.inject.tck

import groovy.transform.PackageScope
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.javax.inject.tck.accessories.Cupholder
import io.micronaut.javax.inject.tck.accessories.RoundThing
import io.micronaut.javax.inject.tck.accessories.SpareTire
import junit.framework.TestCase
import org.junit.Ignore
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.javax.inject.tck.accessories.Cupholder
import io.micronaut.javax.inject.tck.accessories.RoundThing
import io.micronaut.javax.inject.tck.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

class Convertible implements Car {

    @Inject @Drivers Seat driversSeatA
    @Inject @Drivers Seat driversSeatB
    @Inject SpareTire spareTire
    @Inject Cupholder cupholder
    @Inject Provider<Engine> engineProvider

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

    @Inject static Seat staticFieldPlainSeat
    @Inject @Drivers static Seat staticFieldDriversSeat
    @Inject static Tire staticFieldPlainTire
    @Inject @Named("spare") static Tire staticFieldSpareTire
    @Inject static Provider<Seat> staticFieldPlainSeatProvider = nullProvider()
    @Inject @Drivers static Provider<Seat> staticFieldDriversSeatProvider = nullProvider()
    @Inject static Provider<Tire> staticFieldPlainTireProvider = nullProvider()
    @Inject @Named("spare") static Provider<Tire> staticFieldSpareTireProvider = nullProvider()

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

    void setSeat(Seat unused) {
        throw new AssertionError("Unexpected call to non-injectable method")
    }

    @Inject void injectMethodWithZeroArgs() {
        methodWithZeroParamsInjected = true
    }

    @Inject String injectMethodWithNonVoidReturn() {
        methodWithNonVoidReturnInjected = true
        return "unused"
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

    @Inject static void injectStaticMethodWithManyArgs(
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

    static class Tests extends TestCase {

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
                    car.constructorPlainSeat instanceof DriversSeat)
            assertFalse("Expected unqualified value",
                    car.constructorPlainTire instanceof SpareTire)
            assertTrue("Expected qualified value",
                    car.constructorDriversSeat instanceof DriversSeat)
            assertTrue("Expected qualified value",
                    car.constructorSpareTire instanceof SpareTire)
        }

        void testFieldInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.fieldPlainSeat instanceof DriversSeat)
            assertFalse("Expected unqualified value",
                    car.fieldPlainTire instanceof SpareTire)
            assertTrue("Expected qualified value",
                    car.fieldDriversSeat instanceof DriversSeat)
            assertTrue("Expected qualified value",
                    car.fieldSpareTire instanceof SpareTire)
        }

        void testMethodInjectionWithValues() {
            assertFalse("Expected unqualified value",
                    car.methodPlainSeat instanceof DriversSeat)
            assertFalse("Expected unqualified value",
                    car.methodPlainTire instanceof SpareTire)
            assertTrue("Expected qualified value",
                    car.methodDriversSeat instanceof DriversSeat)
            assertTrue("Expected qualified value",
                    car.methodSpareTire instanceof SpareTire)
        }

        // injected providers

        void testConstructorInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.constructorPlainSeatProvider.get() instanceof DriversSeat)
            assertFalse("Expected unqualified value",
                    car.constructorPlainTireProvider.get() instanceof SpareTire)
            assertTrue("Expected qualified value",
                    car.constructorDriversSeatProvider.get() instanceof DriversSeat)
            assertTrue("Expected qualified value",
                    car.constructorSpareTireProvider.get() instanceof SpareTire)
        }

        void testFieldInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.fieldPlainSeatProvider.get() instanceof DriversSeat)
            assertFalse("Expected unqualified value",
                    car.fieldPlainTireProvider.get() instanceof SpareTire)
            assertTrue("Expected qualified value",
                    car.fieldDriversSeatProvider.get() instanceof DriversSeat)
            assertTrue("Expected qualified value",
                    car.fieldSpareTireProvider.get() instanceof SpareTire)
        }

        void testMethodInjectionWithProviders() {
            assertFalse("Expected unqualified value",
                    car.methodPlainSeatProvider.get() instanceof DriversSeat)
            assertFalse("Expected unqualified value",
                    car.methodPlainTireProvider.get() instanceof SpareTire)
            assertTrue("Expected qualified value",
                    car.methodDriversSeatProvider.get() instanceof DriversSeat)
            assertTrue("Expected qualified value",
                    car.methodSpareTireProvider.get() instanceof SpareTire)
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
            assertTrue(((Tire) spareTire).tirePackagePrivateMethod2Injected)
            assertFalse(((RoundThing) spareTire).roundThingPackagePrivateMethod2Injected)

            assertTrue(plainTire.tirePackagePrivateMethod2Injected)
            assertTrue(((RoundThing) plainTire).roundThingPackagePrivateMethod2Injected)
        }

        void testOverriddingMixedWithPackagePrivate3() {
            assertFalse(spareTire.spareTirePackagePrivateMethod3Injected)
            assertTrue(((Tire) spareTire).tirePackagePrivateMethod3Injected)
            assertFalse(((RoundThing) spareTire).roundThingPackagePrivateMethod3Injected)

            assertTrue(plainTire.tirePackagePrivateMethod3Injected)
            assertTrue(((RoundThing) plainTire).roundThingPackagePrivateMethod3Injected)
        }

        void testOverriddingMixedWithPackagePrivate4() {
            assertFalse(plainTire.tirePackagePrivateMethod4Injected)
            assertTrue(((RoundThing) plainTire).roundThingPackagePrivateMethod4Injected)
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
        private final Engine engine = car.engineProvider.get()
        private final SpareTire spareTire = car.spareTire

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