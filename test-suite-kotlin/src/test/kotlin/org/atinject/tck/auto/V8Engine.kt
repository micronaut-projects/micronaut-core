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

import org.atinject.tck.auto.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named

class V8Engine : GasEngine() {
    init {
        publicNoArgsConstructorInjected = true
    }

    @Inject override fun injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            overriddenPackagePrivateMethodInjectedTwice = true
        }
        subPackagePrivateMethodInjected = true
    }

    /**
     * Qualifiers are swapped from how they appear in the superclass.
     */
    override fun injectQualifiers(seatA: Seat, @Drivers seatB: Seat,
                                  tireA: Tire, @Named("spare") tireB: Tire) {
        if (seatA is DriversSeat
                || seatB !is DriversSeat
                || tireA is SpareTire
                || tireB !is SpareTire) {
            qualifiersInheritedFromOverriddenMethod = true
        }
    }

    override fun injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true
    }

    @Inject
    override fun injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true
    }

    override fun injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true
    }
}
