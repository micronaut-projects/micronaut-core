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

import jakarta.inject.Inject
import jakarta.inject.Named

abstract class Engine {

    protected boolean publicNoArgsConstructorInjected
    protected boolean subPackagePrivateMethodInjected
    protected boolean superPackagePrivateMethodInjected
    protected boolean subPackagePrivateMethodForOverrideInjected
    protected boolean superPackagePrivateMethodForOverrideInjected

    protected boolean overriddenTwiceWithOmissionInMiddleInjected
    protected boolean overriddenTwiceWithOmissionInSubclassInjected

    protected Seat seatA
    protected Seat seatB
    protected io.micronaut.jakarta.inject.tck.Tire tireA
    protected io.micronaut.jakarta.inject.tck.Tire tireB

    public boolean overriddenPackagePrivateMethodInjectedTwice
    public boolean qualifiersInheritedFromOverriddenMethod

    @PackageScope @Inject void injectPackagePrivateMethod() {
        superPackagePrivateMethodInjected = true
    }

    @PackageScope @Inject void injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true
    }

    @Inject
    void injectQualifiers(@Drivers Seat seatA, Seat seatB,
                          @Named("spare") io.micronaut.jakarta.inject.tck.Tire tireA, io.micronaut.jakarta.inject.tck.Tire tireB) {
        if (!(seatA instanceof DriversSeat)
                || (seatB instanceof DriversSeat)
                || !(tireA instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)
                || (tireB instanceof io.micronaut.jakarta.inject.tck.accessories.SpareTire)) {
            qualifiersInheritedFromOverriddenMethod = true
        }
    }

    @Inject
    void injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true
    }

    @Inject
    void injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true
    }
}
