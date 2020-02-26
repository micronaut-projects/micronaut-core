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
package org.atinject.tck.auto

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import junit.framework.TestCase
import org.atinject.tck.auto.accessories.Cupholder
import org.atinject.tck.auto.accessories.RoundThing
import org.atinject.tck.auto.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

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

    class Tests : TestCase() {

        private val context = BeanContext.run()
        private val car = context.getBean(Convertible::class.java)
        private val cupholder = car.cupholder
        private val spareTire = car.spareTire
        private val plainTire = car.fieldPlainTire
        private val engine = car.engineProvider!!.get()

        // smoke tests: if these fail all bets are off

        fun testFieldsInjected() {
            TestCase.assertTrue(cupholder != null && spareTire != null)
        }

        fun testProviderReturnedValues() {
            TestCase.assertTrue(engine != null)
        }

        // injecting different kinds of members

        fun testMethodWithZeroParametersInjected() {
            TestCase.assertTrue(car.methodWithZeroParamsInjected)
        }

        fun testMethodWithMultipleParametersInjected() {
            TestCase.assertTrue(car.methodWithMultipleParamsInjected)
        }

        fun testNonVoidMethodInjected() {
            TestCase.assertTrue(car.methodWithNonVoidReturnInjected)
        }

        fun testPublicNoArgsConstructorInjected() {
            TestCase.assertTrue(engine!!.publicNoArgsConstructorInjected)
        }

        fun testSubtypeFieldsInjected() {
            TestCase.assertTrue(spareTire!!.hasSpareTireBeenFieldInjected())
        }

        fun testSubtypeMethodsInjected() {
            TestCase.assertTrue(spareTire!!.hasSpareTireBeenMethodInjected())
        }

        fun testSupertypeFieldsInjected() {
            TestCase.assertTrue(spareTire!!.hasTireBeenFieldInjected())
        }

        fun testSupertypeMethodsInjected() {
            TestCase.assertTrue(spareTire!!.hasTireBeenMethodInjected())
        }

        fun testTwiceOverriddenMethodInjectedWhenMiddleLacksAnnotation() {
            TestCase.assertTrue(engine!!.overriddenTwiceWithOmissionInMiddleInjected)
        }

        // injected values

        fun testQualifiersNotInheritedFromOverriddenMethod() {
            TestCase.assertFalse(engine!!.qualifiersInheritedFromOverriddenMethod)
        }

        fun testConstructorInjectionWithValues() {
            TestCase.assertFalse("Expected unqualified value",
                    car.constructorPlainSeat is DriversSeat)
            TestCase.assertFalse("Expected unqualified value",
                    car.constructorPlainTire is SpareTire)
            TestCase.assertTrue("Expected qualified value",
                    car.constructorDriversSeat is DriversSeat)
            TestCase.assertTrue("Expected qualified value",
                    car.constructorSpareTire is SpareTire)
        }

        fun testFieldInjectionWithValues() {
            TestCase.assertFalse("Expected unqualified value",
                    car.fieldPlainSeat is DriversSeat)
            TestCase.assertFalse("Expected unqualified value",
                    car.fieldPlainTire is SpareTire)
            TestCase.assertTrue("Expected qualified value",
                    car.fieldDriversSeat is DriversSeat)
            TestCase.assertTrue("Expected qualified value",
                    car.fieldSpareTire is SpareTire)
        }

        fun testMethodInjectionWithValues() {
            TestCase.assertFalse("Expected unqualified value",
                    car.methodPlainSeat is DriversSeat)
            TestCase.assertFalse("Expected unqualified value",
                    car.methodPlainTire is SpareTire)
            TestCase.assertTrue("Expected qualified value",
                    car.methodDriversSeat is DriversSeat)
            TestCase.assertTrue("Expected qualified value",
                    car.methodSpareTire is SpareTire)
        }

        // injected providers

        fun testConstructorInjectionWithProviders() {
            TestCase.assertFalse("Expected unqualified value",
                    car.constructorPlainSeatProvider.get() is DriversSeat)
            TestCase.assertFalse("Expected unqualified value",
                    car.constructorPlainTireProvider.get() is SpareTire)
            TestCase.assertTrue("Expected qualified value",
                    car.constructorDriversSeatProvider.get() is DriversSeat)
            TestCase.assertTrue("Expected qualified value",
                    car.constructorSpareTireProvider.get() is SpareTire)
        }

        fun testFieldInjectionWithProviders() {
            TestCase.assertFalse("Expected unqualified value",
                    car.fieldPlainSeatProvider.get() is DriversSeat)
            TestCase.assertFalse("Expected unqualified value",
                    car.fieldPlainTireProvider.get() is SpareTire)
            TestCase.assertTrue("Expected qualified value",
                    car.fieldDriversSeatProvider.get() is DriversSeat)
            TestCase.assertTrue("Expected qualified value",
                    car.fieldSpareTireProvider.get() is SpareTire)
        }

        fun testMethodInjectionWithProviders() {
            TestCase.assertFalse("Expected unqualified value",
                    car.methodPlainSeatProvider.get() is DriversSeat)
            TestCase.assertFalse("Expected unqualified value",
                    car.methodPlainTireProvider.get() is SpareTire)
            TestCase.assertTrue("Expected qualified value",
                    car.methodDriversSeatProvider.get() is DriversSeat)
            TestCase.assertTrue("Expected qualified value",
                    car.methodSpareTireProvider.get() is SpareTire)
        }


        // singletons

        fun testConstructorInjectedProviderYieldsSingleton() {
            TestCase.assertSame("Expected same value",
                    car.constructorPlainSeatProvider.get(), car.constructorPlainSeatProvider.get())
        }

        fun testFieldInjectedProviderYieldsSingleton() {
            TestCase.assertSame("Expected same value",
                    car.fieldPlainSeatProvider.get(), car.fieldPlainSeatProvider.get())
        }

        fun testMethodInjectedProviderYieldsSingleton() {
            TestCase.assertSame("Expected same value",
                    car.methodPlainSeatProvider.get(), car.methodPlainSeatProvider.get())
        }

        fun testCircularlyDependentSingletons() {
            // uses provider.get() to get around circular deps
            assertSame(cupholder!!.seatProvider.get().cupholder, cupholder)
        }


        // non singletons
        fun testSingletonAnnotationNotInheritedFromSupertype() {
            TestCase.assertNotSame(car.driversSeatA, car.driversSeatB)
        }

        fun testConstructorInjectedProviderYieldsDistinctValues() {
            TestCase.assertNotSame("Expected distinct values",
                    car.constructorDriversSeatProvider.get(), car.constructorDriversSeatProvider.get())
            TestCase.assertNotSame("Expected distinct values",
                    car.constructorPlainTireProvider.get(), car.constructorPlainTireProvider.get())
            TestCase.assertNotSame("Expected distinct values",
                    car.constructorSpareTireProvider.get(), car.constructorSpareTireProvider.get())
        }

        fun testFieldInjectedProviderYieldsDistinctValues() {
            TestCase.assertNotSame("Expected distinct values",
                    car.fieldDriversSeatProvider.get(), car.fieldDriversSeatProvider.get())
            TestCase.assertNotSame("Expected distinct values",
                    car.fieldPlainTireProvider.get(), car.fieldPlainTireProvider.get())
            TestCase.assertNotSame("Expected distinct values",
                    car.fieldSpareTireProvider.get(), car.fieldSpareTireProvider.get())
        }

        fun testMethodInjectedProviderYieldsDistinctValues() {
            TestCase.assertNotSame("Expected distinct values",
                    car.methodDriversSeatProvider.get(), car.methodDriversSeatProvider.get())
            TestCase.assertNotSame("Expected distinct values",
                    car.methodPlainTireProvider.get(), car.methodPlainTireProvider.get())
            TestCase.assertNotSame("Expected distinct values",
                    car.methodSpareTireProvider.get(), car.methodSpareTireProvider.get())
        }


        // mix inheritance + visibility

        fun testPackagePrivateMethodInjectedDifferentPackages() {
            TestCase.assertTrue(spareTire!!.subPackagePrivateMethodInjected)
            //Not valid because in Kotlin it is an override
            //TestCase.assertTrue(spareTire.superPackagePrivateMethodInjected)
        }

        fun testOverriddenProtectedMethodInjection() {
            TestCase.assertTrue(spareTire!!.subProtectedMethodInjected)
            TestCase.assertFalse(spareTire.superProtectedMethodInjected)
        }

        fun testOverriddenPublicMethodNotInjected() {
            TestCase.assertTrue(spareTire!!.subPublicMethodInjected)
            TestCase.assertFalse(spareTire.superPublicMethodInjected)
        }


        // inject in order

        fun testFieldsInjectedBeforeMethods() {
            //Added to assert that fields are injected before methods in Kotlin
            TestCase.assertFalse(plainTire!!.methodInjectedBeforeFields)
            //Ignored because fields override in Kotlin
            //TestCase.assertFalse(spareTire!!.methodInjectedBeforeFields)
        }

        fun testSupertypeMethodsInjectedBeforeSubtypeFields() {
            TestCase.assertFalse(spareTire!!.subtypeFieldInjectedBeforeSupertypeMethods)
        }

        fun testSupertypeMethodInjectedBeforeSubtypeMethods() {
            TestCase.assertFalse(spareTire!!.subtypeMethodInjectedBeforeSupertypeMethods)
        }


        // necessary injections occur

        fun testPackagePrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            //Not valid because in Kotlin the method is overriden
            //TestCase.assertTrue(spareTire!!.subPackagePrivateMethodForOverrideInjected)
        }


        // override or similar method without @Inject

        fun testPrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            TestCase.assertFalse(spareTire!!.superPrivateMethodForOverrideInjected)
        }

        fun testPackagePrivateMethodNotInjectedWhenOverrideLacksAnnotation() {
            TestCase.assertFalse(engine!!.subPackagePrivateMethodForOverrideInjected)
            TestCase.assertFalse(engine.superPackagePrivateMethodForOverrideInjected)
        }

        fun testPackagePrivateMethodNotInjectedWhenSupertypeHasAnnotatedSimilarMethod() {
            TestCase.assertFalse(spareTire!!.superPackagePrivateMethodForOverrideInjected)
        }

        fun testProtectedMethodNotInjectedWhenOverrideNotAnnotated() {
            TestCase.assertFalse(spareTire!!.protectedMethodForOverrideInjected)
        }

        fun testPublicMethodNotInjectedWhenOverrideNotAnnotated() {
            TestCase.assertFalse(spareTire!!.publicMethodForOverrideInjected)
        }

        fun testTwiceOverriddenMethodNotInjectedWhenOverrideLacksAnnotation() {
            TestCase.assertFalse(engine!!.overriddenTwiceWithOmissionInSubclassInjected)
        }

        fun testOverriddingMixedWithPackagePrivate2() {
            TestCase.assertTrue(spareTire!!.spareTirePackagePrivateMethod2Injected)
            //Not valid in Kotlin because the method is overridden
            //TestCase.assertTrue((spareTire as Tire).tirePackagePrivateMethod2Injected)
            TestCase.assertFalse((spareTire as RoundThing).roundThingPackagePrivateMethod2Injected)

            TestCase.assertTrue(plainTire!!.tirePackagePrivateMethod2Injected)
            //Not valid in Kotlin because the method is overridden
            //TestCase.assertTrue((plainTire as RoundThing).roundThingPackagePrivateMethod2Injected)
        }

        fun testOverriddingMixedWithPackagePrivate3() {
            TestCase.assertFalse(spareTire!!.spareTirePackagePrivateMethod3Injected)
            //Not valid in Kotlin because the method is overridden
            //TestCase.assertTrue((spareTire as Tire).tirePackagePrivateMethod3Injected)
            TestCase.assertFalse((spareTire as RoundThing).roundThingPackagePrivateMethod3Injected)

            TestCase.assertTrue(plainTire!!.tirePackagePrivateMethod3Injected)
            //Not valid in Kotlin because the method is overridden
            //TestCase.assertTrue((plainTire as RoundThing).roundThingPackagePrivateMethod3Injected)
        }

        fun testOverriddingMixedWithPackagePrivate4() {
            TestCase.assertFalse(plainTire!!.tirePackagePrivateMethod4Injected)
            //Not the same as Java because package private can be overridden by any subclass in the project
            //TestCase.assertTrue((plainTire as RoundThing).roundThingPackagePrivateMethod4Injected)
        }

        // inject only once

        fun testOverriddenPackagePrivateMethodInjectedOnlyOnce() {
            TestCase.assertFalse(engine!!.overriddenPackagePrivateMethodInjectedTwice)
        }

        fun testSimilarPackagePrivateMethodInjectedOnlyOnce() {
            TestCase.assertFalse(spareTire!!.similarPackagePrivateMethodInjectedTwice)
        }

        fun testOverriddenProtectedMethodInjectedOnlyOnce() {
            TestCase.assertFalse(spareTire!!.overriddenProtectedMethodInjectedTwice)
        }

        fun testOverriddenPublicMethodInjectedOnlyOnce() {
            TestCase.assertFalse(spareTire!!.overriddenPublicMethodInjectedTwice)
        }

    }

    class PrivateTests : TestCase() {
        private val context = DefaultBeanContext().start()
        private val car = context.getBean(Convertible::class.java)
        private val engine = car.engineProvider!!.get()
        private val spareTire = car.spareTire

        fun testSupertypePrivateMethodInjected() {
            TestCase.assertTrue(spareTire!!.superPrivateMethodInjected)
            TestCase.assertTrue(spareTire.subPrivateMethodInjected)
        }

        fun testPackagePrivateMethodInjectedSamePackage() {
            TestCase.assertTrue(engine.subPackagePrivateMethodInjected)
            TestCase.assertFalse(engine.superPackagePrivateMethodInjected)
        }

        fun testPrivateMethodInjectedEvenWhenSimilarMethodLacksAnnotation() {
            TestCase.assertTrue(spareTire!!.subPrivateMethodForOverrideInjected)
        }

        fun testSimilarPrivateMethodInjectedOnlyOnce() {
            TestCase.assertFalse(spareTire!!.similarPrivateMethodInjectedTwice)
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
