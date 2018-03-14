/*
 * Copyright (C) 2009 The JSR-330 Expert Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atinject.tck.auto

import junit.framework.Test
import junit.framework.TestSuite

/**
 * Manufactures the compatibility test suite. This TCK relies on
 * [JUnit](http://junit.org/). To integrate the TCK with your
 * injector, create a JUnit test suite class that passes an injected
 * [Car] instance to [testsFor(Car)][.testsFor]:
 *
 *
 *
 * The static `suite` method that returns a `Test` is a JUnit
 * convention. Feel free to run the returned tests in other ways.
 *
 *
 * Configure the injector as follows:
 *
 *
 *  * [org.atinject.tck.auto.Car] is implemented by
 * [Convertible][org.atinject.tck.auto.Convertible].
 *  * [@Drivers][org.atinject.tck.auto.Drivers]
 * [Seat][org.atinject.tck.auto.Seat] is
 * implemented by [DriversSeat][org.atinject.tck.auto.DriversSeat].
 *  * [Seat][org.atinject.tck.auto.Seat] is
 * implemented by [Seat][org.atinject.tck.auto.Seat] itself, and
 * [Tire][org.atinject.tck.auto.Tire] by
 * [Tire][org.atinject.tck.auto.Tire] itself
 * (not subclasses).
 *  * [Engine][org.atinject.tck.auto.Engine] is implemented by
 * [V8Engine][org.atinject.tck.auto.V8Engine].
 *  * [@Named(&quot;spare&quot;)][javax.inject.Named]
 * [Tire][org.atinject.tck.auto.Tire] is implemented by
 * [SpareTire][org.atinject.tck.auto.accessories.SpareTire].
 *  * The following classes may also be injected directly:
 * [Cupholder][org.atinject.tck.auto.accessories.Cupholder],
 * [SpareTire][org.atinject.tck.auto.accessories.SpareTire], and
 * [FuelTank][org.atinject.tck.auto.FuelTank].
 *
 *
 *
 * Static and private member injection support is optional, but if your
 * injector supports those features, it must pass the respective tests. If
 * static member injection is supported, the static members of the following
 * types shall also be injected once:
 * [Convertible][org.atinject.tck.auto.Convertible],
 * [Tire][org.atinject.tck.auto.Tire], and
 * [SpareTire][org.atinject.tck.auto.accessories.SpareTire].
 *
 *
 * Use your favorite JUnit tool to run the tests. For example, you can use
 * your IDE or JUnit's command line runner:
 *
 * <pre>
 * java -cp javax.inject-tck.jar:junit.jar:myinjector.jar \
 * junit.textui.TestRunner MyTck</pre>
 */
object Tck {

    /**
     * Constructs a JUnit test suite for the given [Car] instance.
     *
     * @param car to test
     * @param supportsStatic true if the injector supports static member
     * injection
     * @param supportsPrivate true if the injector supports private member
     * injection
     *
     * @throws NullPointerException if car is null
     * @throws ClassCastException if car doesn't extend
     * [Convertible]
     */
    fun testsFor(car: Car?, supportsStatic: Boolean,
                 supportsPrivate: Boolean): Test {
        if (car == null) {
            throw NullPointerException("car")
        }

        if (!(car is Convertible)) {
            throw ClassCastException("car doesn't implement Convertible")
        }

        Convertible.localConvertible.set(car as Convertible?)
        try {
            val suite = TestSuite(Convertible.Tests::class.java)
            if (supportsStatic) {
//                suite.addTestSuite(Convertible.StaticTests.class);
            }
            if (supportsPrivate) {
                suite.addTestSuite(Convertible.PrivateTests::class.java)
            }
            return suite
        } finally {
            Convertible.localConvertible.remove()
        }
    }
}
