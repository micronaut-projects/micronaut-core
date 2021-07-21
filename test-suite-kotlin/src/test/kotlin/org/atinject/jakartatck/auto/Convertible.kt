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

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import org.atinject.jakartatck.auto.accessories.Cupholder
import org.atinject.jakartatck.auto.accessories.RoundThing
import org.atinject.jakartatck.auto.accessories.SpareTire
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider
import org.junit.jupiter.api.TestInstance

open class Convertible : Car {

    @Inject @field:Drivers internal var driversSeatA: Seat? = null
    @Inject @field:Drivers internal var driversSeatB: Seat? = null
    @Inject internal var spareTire: SpareTire? = null
    @Inject internal var cupholder: Cupholder? = null
    @Inject internal var engineProvider: Provider<Engine>? = null

    private var methodWithZeroParamsInjected: Boolean = false
    private var methodWithMultipleParamsInjected: Boolean = false
    private var methodWithNonVoidReturnInjected: Boolean = false

    private var constructorPlainSeat: Seat? = null
    private var constructorDriversSeat: Seat? = null
    private var constructorPlainTire: Tire? = null
    private var constructorSpareTire: Tire? = null
    private var constructorPlainSeatProvider = nullProvider<Seat>()
    private var constructorDriversSeatProvider = nullProvider<Seat>()
    private var constructorPlainTireProvider = nullProvider<Tire>()
    private var constructorSpareTireProvider = nullProvider<Tire>()

    @Inject protected var fieldPlainSeat: Seat? = null
    @Inject @field:Drivers protected var fieldDriversSeat: Seat? = null
    @Inject protected var fieldPlainTire: Tire? = null
    @Inject @field:Named("spare") protected var fieldSpareTire: Tire? = null
    @Inject protected var fieldPlainSeatProvider = nullProvider<Seat>()
    @Inject @field:Drivers protected var fieldDriversSeatProvider = nullProvider<Seat>()
    @Inject protected var fieldPlainTireProvider = nullProvider<Tire>()
    @Inject @field:Named("spare") protected var fieldSpareTireProvider = nullProvider<Tire>()

    private var methodPlainSeat: Seat? = null
    private var methodDriversSeat: Seat? = null
    private var methodPlainTire: Tire? = null
    private var methodSpareTire: Tire? = null
    private var methodPlainSeatProvider = nullProvider<Seat>()
    private var methodDriversSeatProvider = nullProvider<Seat>()
    private var methodPlainTireProvider = nullProvider<Tire>()
    private var methodSpareTireProvider = nullProvider<Tire>()

    @Inject internal constructor(
            plainSeat: Seat,
            @Drivers driversSeat: Seat,
            plainTire: Tire,
            @Named("spare") spareTire: Tire,
            plainSeatProvider: Provider<Seat>,
            @Drivers driversSeatProvider: Provider<Seat>,
            plainTireProvider: Provider<Tire>,
            @Named("spare") spareTireProvider: Provider<Tire>) {
        constructorPlainSeat = plainSeat
        constructorDriversSeat = driversSeat
        constructorPlainTire = plainTire
        constructorSpareTire = spareTire
        constructorPlainSeatProvider = plainSeatProvider
        constructorDriversSeatProvider = driversSeatProvider
        constructorPlainTireProvider = plainTireProvider
        constructorSpareTireProvider = spareTireProvider
    }

    internal constructor() {
        throw AssertionError("Unexpected call to non-injectable constructor")
    }

    internal fun setSeat(unused: Seat) {
        throw AssertionError("Unexpected call to non-injectable method")
    }

    @Inject internal fun injectMethodWithZeroArgs() {
        methodWithZeroParamsInjected = true
    }

    @Inject internal fun injectMethodWithNonVoidReturn(): String {
        methodWithNonVoidReturnInjected = true
        return "unused"
    }

    @Inject internal fun injectInstanceMethodWithManyArgs(
            plainSeat: Seat,
            @Drivers driversSeat: Seat,
            plainTire: Tire,
            @Named("spare") spareTire: Tire,
            plainSeatProvider: Provider<Seat>,
            @Drivers driversSeatProvider: Provider<Seat>,
            plainTireProvider: Provider<Tire>,
            @Named("spare") spareTireProvider: Provider<Tire>) {
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

    internal class NullProvider<T> : Provider<T> {

        override fun get(): T? {
            return null
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Tests {

        private val context = BeanContext.run()
        private val car = context.getBean(Convertible::class.java)
        private val cupholder = car.cupholder
        private val spareTire = car.spareTire
        private val plainTire = car.fieldPlainTire
        private val engine = car.engineProvider!!.get()

        // smoke tests: if these fail all bets are off

        @Test
        fun testFieldsInjected() {
            assertTrue(cupholder != null && spareTire != null)
        }

        @Test
        fun testProviderReturnedValues() {
            assertTrue(engine != null)
        }

        // injecting different kinds of members

        @Test
        fun testMethodWithZeroParametersInjected() {
            assertTrue(car.methodWithZeroParamsInjected)
        }

        @Test
        fun testMethodWithMultipleParametersInjected() {
            assertTrue(car.methodWithMultipleParamsInjected)
        }

        @Test
        fun testNonVoidMethodInjected() {
            assertTrue(car.methodWithNonVoidReturnInjected)
        }

        @Test
        fun testPublicNoArgsConstructorInjected() {
            assertTrue(engine!!.publicNoArgsConstructorInjected)
        }

        @Test
        fun testSubtypeFieldsInjected() {
            assertTrue(spareTire!!.hasSpareTireBeenFieldInjected())
        }

        @Test
        fun testSubtypeMethodsInjected() {
            assertTrue(spareTire!!.hasSpareTireBeenMethodInjected())
        }

        @Test
        fun testSupertypeFieldsInjected() {
            assertTrue(spareTire!!.hasTireBeenFieldInjected())
        }

        @Test
        fun testSupertypeMethodsInjected() {
            assertTrue(spareTire!!.hasTireBeenMethodInjected())
        }

        @Test
        fun testTwiceOverriddenMethodInjectedWhenMiddleLacksAnnotation() {
            assertTrue(engine!!.overriddenTwiceWithOmissionInMiddleInjected)
        }

        // injected values

/*        @Test
        fun testQualifiersNotInheritedFromOverriddenMethod() {
            assertTrue(engine!!.overriddenMethodInjected)
            assertFalse(engine!!.qualifiersInheritedFromOverriddenMethod)
        }*/

        @Test
        fun testConstructorInjectionWithValues() {
            assertFalse(car.constructorPlainSeat is DriversSeat,"Expected unqualified value")
            assertFalse(car.constructorPlainTire is SpareTire,"Expected unqualified value")
            assertTrue(car.constructorDriversSeat is DriversSeat,"Expected qualified value")
            assertTrue(car.constructorSpareTire is SpareTire,"Expected qualified value")
        }

        @Test
        fun testFieldInjectionWithValues() {
            assertFalse(car.fieldPlainSeat is DriversSeat,"Expected unqualified value")
            assertFalse(car.fieldPlainTire is SpareTire,"Expected unqualified value")
            assertTrue(car.fieldDriversSeat is DriversSeat,"Expected qualified value")
            assertTrue(car.fieldSpareTire is SpareTire,"Expected qualified value")
        }

        @Test
        fun testMethodInjectionWithValues() {
            assertFalse(car.methodPlainSeat is DriversSeat,"Expected unqualified value")
            assertFalse(car.methodPlainTire is SpareTire,"Expected unqualified value")
            assertTrue(car.methodDriversSeat is DriversSeat,"Expected qualified value")
            assertTrue(car.methodSpareTire is SpareTire,"Expected qualified value")
        }

        // injected providers

        @Test
        fun testConstructorInjectionWithProviders() {
            assertFalse(car.constructorPlainSeatProvider.get() is DriversSeat,"Expected unqualified value")
            assertFalse(car.constructorPlainTireProvider.get() is SpareTire,"Expected unqualified value")
            assertTrue(car.constructorDriversSeatProvider.get() is DriversSeat,"Expected qualified value")
            assertTrue(car.constructorSpareTireProvider.get() is SpareTire,"Expected qualified value")
        }

        @Test
        fun testFieldInjectionWithProviders() {
            assertFalse(car.fieldPlainSeatProvider.get() is DriversSeat,"Expected unqualified value")
            assertFalse(car.fieldPlainTireProvider.get() is SpareTire,"Expected unqualified value")
            assertTrue(car.fieldDriversSeatProvider.get() is DriversSeat,"Expected qualified value")
            assertTrue(car.fieldSpareTireProvider.get() is SpareTire,"Expected qualified value")
        }

        @Test
        fun testMethodInjectionWithProviders() {
            assertFalse(car.methodPlainSeatProvider.get() is DriversSeat,"Expected unqualified value")
            assertFalse(car.methodPlainTireProvider.get() is SpareTire,"Expected unqualified value")
            assertTrue(car.methodDriversSeatProvider.get() is DriversSeat,"Expected qualified value")
            assertTrue(car.methodSpareTireProvider.get() is SpareTire,"Expected qualified value")
        }


        // singletons

        @Test
        fun testConstructorInjectedProviderYieldsSingleton() {
            assertSame(car.constructorPlainSeatProvider.get(), car.constructorPlainSeatProvider.get(),"Expected same value")
        }

        @Test
        fun testFieldInjectedProviderYieldsSingleton() {
            assertSame(car.fieldPlainSeatProvider.get(), car.fieldPlainSeatProvider.get(),"Expected same value")
        }

        @Test
        fun testMethodInjectedProviderYieldsSingleton() {
            assertSame(car.methodPlainSeatProvider.get(), car.methodPlainSeatProvider.get(),"Expected same value")
        }

        @Test
        fun testCircularlyDependentSingletons() {
            // uses provider.get() to get around circular deps
            assertSame(cupholder!!.seatProvider.get().cupholder, cupholder)
        }


        // non singletons
        @Test
        fun testSingletonAnnotationNotInheritedFromSupertype() {
            assertNotSame(car.driversSeatA, car.driversSeatB)
        }

        @Test
        fun testConstructorInjectedProviderYieldsDistinctValues() {
            assertNotSame(car.constructorDriversSeatProvider.get(), car.constructorDriversSeatProvider.get(),"Expected distinct values")
            assertNotSame(car.constructorPlainTireProvider.get(), car.constructorPlainTireProvider.get(),"Expected distinct values")
            assertNotSame(car.constructorSpareTireProvider.get(), car.constructorSpareTireProvider.get(),"Expected distinct values")
        }

        @Test
        fun testFieldInjectedProviderYieldsDistinctValues() {
            assertNotSame(car.fieldDriversSeatProvider.get(), car.fieldDriversSeatProvider.get(),"Expected distinct values")
            assertNotSame(car.fieldPlainTireProvider.get(), car.fieldPlainTireProvider.get(),"Expected distinct values")
            assertNotSame(car.fieldSpareTireProvider.get(), car.fieldSpareTireProvider.get(),"Expected distinct values")
        }

        @Test
        fun testMethodInjectedProviderYieldsDistinctValues() {
            assertNotSame(car.methodDriversSeatProvider.get(), car.methodDriversSeatProvider.get(),"Expected distinct values")
            assertNotSame(car.methodPlainTireProvider.get(), car.methodPlainTireProvider.get(),"Expected distinct values")
            assertNotSame(car.methodSpareTireProvider.get(), car.methodSpareTireProvider.get(),"Expected distinct values")
        }


        // mix inheritance + visibility

        @Test
        fun testPackagePrivateMethodInjectedDifferentPackages() {
            assertTrue(spareTire!!.subPackagePrivateMethodInjected)
            //Not valid because in Kotlin it is an override
            //assertTrue(spareTire.superPackagePrivateMethodInjected)
        }

        @Test
        fun testOverriddenProtectedMethodInjection() {
            assertTrue(spareTire!!.subProtectedMethodInjected)
            assertFalse(spareTire.superProtectedMethodInjected)
        }

        @Test
        fun testOverriddenPublicMethodNotInjected() {
            assertTrue(spareTire!!.subPublicMethodInjected)
            assertFalse(spareTire.superPublicMethodInjected)
        }


        // inject in order

        @Test
        fun testFieldsInjectedBeforeMethods() {
            //Added to assert that fields are injected before methods in Kotlin
            assertFalse(plainTire!!.methodInjectedBeforeFields)
            //Ignored because fields override in Kotlin
            //assertFalse(spareTire!!.methodInjectedBeforeFields)
        }

        @Test
        fun testSupertypeMethodsInjectedBeforeSubtypeFields() {
            assertFalse(spareTire!!.subtypeFieldInjectedBeforeSupertypeMethods)
        }

        @Test
        fun testSupertypeMethodInjectedBeforeSubtypeMethods() {
            assertFalse(spareTire!!.subtypeMethodInjectedBeforeSupertypeMethods)
        }


        // necessary injections occur

        @Test
        fun testPackagePrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            //Not valid because in Kotlin the method is overridden
            //assertTrue(spareTire!!.subPackagePrivateMethodForOverrideInjected)
        }


        // override or similar method without @Inject

        @Test
        fun testPrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire!!.superPrivateMethodForOverrideInjected)
        }

        @Test
        fun testPackagePrivateMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine!!.subPackagePrivateMethodForOverrideInjected)
            assertFalse(engine.superPackagePrivateMethodForOverrideInjected)
        }

        @Test
        fun testPackagePrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            assertFalse(spareTire!!.superPackagePrivateMethodForOverrideInjected)
        }

        @Test
        fun testProtectedMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire!!.protectedMethodForOverrideInjected)
        }

        @Test
        fun testPublicMethodNotInjectedWhenOverrideNotAnnotated() {
            assertFalse(spareTire!!.publicMethodForOverrideInjected)
        }

        @Test
        fun testTwiceOverriddenMethodNotInjectedWhenOverrideLacksAnnotation() {
            assertFalse(engine!!.overriddenTwiceWithOmissionInSubclassInjected)
        }

        @Test
        fun testOverridingMixedWithPackagePrivate2() {
            assertTrue(spareTire!!.spareTirePackagePrivateMethod2Injected)
            //Not valid in Kotlin because the method is overridden
            //assertTrue((spareTire as Tire).tirePackagePrivateMethod2Injected)
            assertFalse((spareTire as RoundThing).roundThingPackagePrivateMethod2Injected)

            assertTrue(plainTire!!.tirePackagePrivateMethod2Injected)
            //Not valid in Kotlin because the method is overridden
            //assertTrue((plainTire as RoundThing).roundThingPackagePrivateMethod2Injected)
        }

        @Test
        fun testOverridingMixedWithPackagePrivate3() {
            assertFalse(spareTire!!.spareTirePackagePrivateMethod3Injected)
            //Not valid in Kotlin because the method is overridden
            //assertTrue((spareTire as Tire).tirePackagePrivateMethod3Injected)
            assertFalse((spareTire as RoundThing).roundThingPackagePrivateMethod3Injected)

            assertTrue(plainTire!!.tirePackagePrivateMethod3Injected)
            //Not valid in Kotlin because the method is overridden
            //assertTrue((plainTire as RoundThing).roundThingPackagePrivateMethod3Injected)
        }

        @Test
        fun testOverridingMixedWithPackagePrivate4() {
            assertFalse(plainTire!!.tirePackagePrivateMethod4Injected)
            //Not the same as Java because package private can be overridden by any subclass in the project
            //assertTrue((plainTire as RoundThing).roundThingPackagePrivateMethod4Injected)
        }

        // inject only once

        @Test
        fun testOverriddenPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(engine!!.overriddenPackagePrivateMethodInjectedTwice)
        }

        @Test
        fun testSimilarPackagePrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire!!.similarPackagePrivateMethodInjectedTwice)
        }

        @Test
        fun testOverriddenProtectedMethodInjectedOnlyOnce() {
            assertFalse(spareTire!!.overriddenProtectedMethodInjectedTwice)
        }

        @Test
        fun testOverriddenPublicMethodInjectedOnlyOnce() {
            assertFalse(spareTire!!.overriddenPublicMethodInjectedTwice)
        }

    }

    class PrivateTests {
        private val context = DefaultBeanContext().start()
        private val car = context.getBean(Convertible::class.java)
        private val engine = car.engineProvider!!.get()
        private val spareTire = car.spareTire

        @Test
        fun testSupertypePrivateMethodInjected() {
            assertTrue(spareTire!!.superPrivateMethodInjected)
            assertTrue(spareTire.subPrivateMethodInjected)
        }

        @Test
        fun testPackagePrivateMethodInjectedSamePackage() {
            assertTrue(engine.subPackagePrivateMethodInjected)
            assertFalse(engine.superPackagePrivateMethodInjected)
        }

        @Test
        fun testPrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            assertTrue(spareTire!!.subPrivateMethodForOverrideInjected)
        }

        @Test
        fun testSimilarPrivateMethodInjectedOnlyOnce() {
            assertFalse(spareTire!!.similarPrivateMethodInjectedTwice)
        }
    }

    companion object {

        @Inject internal var staticFieldPlainSeat: Seat? = null
        @Inject
        @Drivers internal var staticFieldDriversSeat: Seat? = null
        @Inject internal var staticFieldPlainTire: Tire? = null
        @Inject
        @Named("spare") internal var staticFieldSpareTire: Tire? = null
        @Inject internal var staticFieldPlainSeatProvider = nullProvider<Seat>()
        @Inject
        @Drivers internal var staticFieldDriversSeatProvider = nullProvider<Seat>()
        @Inject internal var staticFieldPlainTireProvider = nullProvider<Tire>()
        @Inject
        @Named("spare") internal var staticFieldSpareTireProvider = nullProvider<Tire>()

        private var staticMethodPlainSeat: Seat? = null
        private var staticMethodDriversSeat: Seat? = null
        private var staticMethodPlainTire: Tire? = null
        private var staticMethodSpareTire: Tire? = null
        private var staticMethodPlainSeatProvider = nullProvider<Seat>()
        private var staticMethodDriversSeatProvider = nullProvider<Seat>()
        private var staticMethodPlainTireProvider = nullProvider<Tire>()
        private var staticMethodSpareTireProvider = nullProvider<Tire>()

        @Inject internal fun injectStaticMethodWithManyArgs(
                plainSeat: Seat,
                @Drivers driversSeat: Seat,
                plainTire: Tire,
                @Named("spare") spareTire: Tire,
                plainSeatProvider: Provider<Seat>,
                @Drivers driversSeatProvider: Provider<Seat>,
                plainTireProvider: Provider<Tire>,
                @Named("spare") spareTireProvider: Provider<Tire>) {
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
        private fun <T> nullProvider(): Provider<T> {
            return NullProvider()
        }

        var localConvertible = ThreadLocal<Convertible>()
    }
}
