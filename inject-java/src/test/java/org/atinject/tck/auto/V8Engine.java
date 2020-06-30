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

import org.atinject.tck.auto.accessories.SpareTire;

import javax.inject.Inject;
import javax.inject.Named;

public class V8Engine extends GasEngine {

    public V8Engine() {
        publicNoArgsConstructorInjected = true;
    }

    @Override
    @Inject void injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            overriddenPackagePrivateMethodInjectedTwice = true;
        }
        subPackagePrivateMethodInjected = true;
    }

    /**
     * Qualifiers are swapped from how they appear in the superclass.
     */
    @Override
    public void injectQualifiers(Seat seatA, @Drivers Seat seatB,
            Tire tireA, @Named("spare") Tire tireB) {
        if ((seatA instanceof DriversSeat)
                || !(seatB instanceof DriversSeat)
                || (tireA instanceof SpareTire)
                || !(tireB instanceof SpareTire)) {
            qualifiersInheritedFromOverriddenMethod = true;
        }
    }

    @Override
    void injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true;
    }

    @Override
    @Inject
    public void injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true;
    }

    @Override
    public void injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true;
    }
}
