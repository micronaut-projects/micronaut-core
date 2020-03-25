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
package io.micronaut.javax.inject.tck.accessories

import groovy.transform.PackageScope
import io.micronaut.javax.inject.tck.FuelTank
import io.micronaut.javax.inject.tck.Tire

import javax.inject.Inject

class SpareTire extends Tire {

    @PackageScope FuelTank constructorInjection = NEVER_INJECTED
    @Inject protected FuelTank fieldInjection = NEVER_INJECTED
    @PackageScope FuelTank methodInjection = NEVER_INJECTED
    @Inject @PackageScope static FuelTank staticFieldInjection = NEVER_INJECTED
    @PackageScope static FuelTank staticMethodInjection = NEVER_INJECTED

    @Inject
    SpareTire(FuelTank forSupertype, FuelTank forSubtype) {
        super(forSupertype)
        this.constructorInjection = forSubtype
    }

    @Inject @PackageScope void subtypeMethodInjection(FuelTank methodInjection) {
        if (!hasSpareTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true
        }
        this.methodInjection = methodInjection
    }

    @Inject @PackageScope static void subtypeStaticMethodInjection(FuelTank methodInjection) {
        if (!hasBeenStaticFieldInjected()) {
            staticMethodInjectedBeforeStaticFields = true
        }
        staticMethodInjection = methodInjection
    }

    @Inject private void injectPrivateMethod() {
        if (subPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true
        }
        subPrivateMethodInjected = true
    }

    @Inject @PackageScope void injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true
        }
        subPackagePrivateMethodInjected = true
    }

    @Inject protected void injectProtectedMethod() {
        if (subProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true
        }
        subProtectedMethodInjected = true
    }

    @Inject
    void injectPublicMethod() {
        if (subPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true
        }
        subPublicMethodInjected = true
    }

    private void injectPrivateMethodForOverride() {
        superPrivateMethodForOverrideInjected = true
    }

    @PackageScope void injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true
    }

    protected void injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true
    }

    void injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true
    }

    boolean hasSpareTireBeenFieldInjected() {
        return fieldInjection != NEVER_INJECTED
    }

    boolean hasSpareTireBeenMethodInjected() {
        return methodInjection != NEVER_INJECTED
    }

    static boolean hasBeenStaticFieldInjected() {
        return staticFieldInjection != NEVER_INJECTED
    }

    static boolean hasBeenStaticMethodInjected() {
        return staticMethodInjection != NEVER_INJECTED
    }

    private boolean spareTirePackagePrivateMethod2Injected

    boolean getSpareTirePackagePrivateMethod2Injected() {
        return spareTirePackagePrivateMethod2Injected
    }

    @Override @Inject @PackageScope void injectPackagePrivateMethod2() {
        spareTirePackagePrivateMethod2Injected = true
    }

    private boolean spareTirePackagePrivateMethod3Injected

    boolean getSpareTirePackagePrivateMethod3Injected() {
        return spareTirePackagePrivateMethod3Injected
    }

    @PackageScope void injectPackagePrivateMethod3() {
        spareTirePackagePrivateMethod3Injected = true
    }
}
