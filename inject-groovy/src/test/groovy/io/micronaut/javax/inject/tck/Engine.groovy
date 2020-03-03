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
package io.micronaut.javax.inject.tck

import groovy.transform.PackageScope
import io.micronaut.javax.inject.tck.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named

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
    protected Tire tireA
    protected Tire tireB

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
                          @Named("spare") Tire tireA, Tire tireB) {
        if (!(seatA instanceof DriversSeat)
                || (seatB instanceof DriversSeat)
                || !(tireA instanceof SpareTire)
                || (tireB instanceof SpareTire)) {
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
