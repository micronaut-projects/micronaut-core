/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atinject.tck.auto.accessories;

import org.atinject.tck.auto.FuelTank;
import org.atinject.tck.auto.Tire;

import javax.inject.Inject;

public class SpareTire extends Tire {

    FuelTank constructorInjection = NEVER_INJECTED;
    @Inject protected FuelTank fieldInjection = NEVER_INJECTED;
    FuelTank methodInjection = NEVER_INJECTED;
    @Inject static FuelTank staticFieldInjection = NEVER_INJECTED;
    static FuelTank staticMethodInjection = NEVER_INJECTED;

    @Inject
    public SpareTire(FuelTank forSupertype, FuelTank forSubtype) {
        super(forSupertype);
        this.constructorInjection = forSubtype;
    }

    @Inject void subtypeMethodInjection(FuelTank methodInjection) {
        if (!hasSpareTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true;
        }
        this.methodInjection = methodInjection;
    }

    @Inject static void subtypeStaticMethodInjection(FuelTank methodInjection) {
        if (!hasBeenStaticFieldInjected()) {
            staticMethodInjectedBeforeStaticFields = true;
        }
        staticMethodInjection = methodInjection;
    }

    @Inject private void injectPrivateMethod() {
        if (subPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true;
        }
        subPrivateMethodInjected = true;
    }

    @Inject void injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true;
        }
        subPackagePrivateMethodInjected = true;
    }

    @Override
    @Inject protected void injectProtectedMethod() {
        if (subProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true;
        }
        subProtectedMethodInjected = true;
    }

    @Override
    @Inject
    public void injectPublicMethod() {
        if (subPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true;
        }
        subPublicMethodInjected = true;
    }

    private void injectPrivateMethodForOverride() {
        superPrivateMethodForOverrideInjected = true;
    }

    void injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true;
    }

    @Override
    protected void injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true;
    }

    @Override
    public void injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true;
    }

    @Override
    public boolean hasSpareTireBeenFieldInjected() {
        return fieldInjection != NEVER_INJECTED;
    }

    @Override
    public boolean hasSpareTireBeenMethodInjected() {
        return methodInjection != NEVER_INJECTED;
    }

    public static boolean hasBeenStaticFieldInjected() {
        return staticFieldInjection != NEVER_INJECTED;
    }

    public static boolean hasBeenStaticMethodInjected() {
        return staticMethodInjection != NEVER_INJECTED;
    }

    private boolean spareTirePackagePrivateMethod2Injected;

    public boolean getSpareTirePackagePrivateMethod2Injected() {
        return spareTirePackagePrivateMethod2Injected;
    }

    @Override
    @Inject void injectPackagePrivateMethod2() {
        spareTirePackagePrivateMethod2Injected = true;
    }

    private boolean spareTirePackagePrivateMethod3Injected;

    public boolean getSpareTirePackagePrivateMethod3Injected() {
        return spareTirePackagePrivateMethod3Injected;
    }

    @Override
    void injectPackagePrivateMethod3() {
        spareTirePackagePrivateMethod3Injected = true;
    }
}
