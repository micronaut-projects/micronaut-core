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

import org.atinject.tck.auto.accessories.RoundThing;
import org.atinject.tck.auto.accessories.SpareTire;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

public class Tire extends RoundThing {

    protected static final FuelTank NEVER_INJECTED = new FuelTank();

    protected static final Set<String> moreProblems = new LinkedHashSet<String>();

    FuelTank constructorInjection = NEVER_INJECTED;
    @Inject protected FuelTank fieldInjection = NEVER_INJECTED;
    FuelTank methodInjection = NEVER_INJECTED;
    @Inject static FuelTank staticFieldInjection = NEVER_INJECTED;
    static FuelTank staticMethodInjection = NEVER_INJECTED;

    boolean constructorInjected;

    protected boolean superPrivateMethodInjected;
    protected boolean superPackagePrivateMethodInjected;
    protected boolean superProtectedMethodInjected;
    protected boolean superPublicMethodInjected;
    protected boolean subPrivateMethodInjected;
    protected boolean subPackagePrivateMethodInjected;
    protected boolean subProtectedMethodInjected;
    protected boolean subPublicMethodInjected;

    protected boolean superPrivateMethodForOverrideInjected;
    protected boolean superPackagePrivateMethodForOverrideInjected;
    protected boolean subPrivateMethodForOverrideInjected;
    protected boolean subPackagePrivateMethodForOverrideInjected;
    protected boolean protectedMethodForOverrideInjected;
    protected boolean publicMethodForOverrideInjected;

    public boolean methodInjectedBeforeFields;
    public boolean subtypeFieldInjectedBeforeSupertypeMethods;
    public boolean subtypeMethodInjectedBeforeSupertypeMethods;
    public static boolean staticMethodInjectedBeforeStaticFields;
    public static boolean subtypeStaticFieldInjectedBeforeSupertypeStaticMethods;
    public static boolean subtypeStaticMethodInjectedBeforeSupertypeStaticMethods;
    public boolean similarPrivateMethodInjectedTwice;
    public boolean similarPackagePrivateMethodInjectedTwice;
    public boolean overriddenProtectedMethodInjectedTwice;
    public boolean overriddenPublicMethodInjectedTwice;

    @Inject
    public Tire(FuelTank constructorInjection) {
        this.constructorInjection = constructorInjection;
    }

    @Inject void supertypeMethodInjection(FuelTank methodInjection) {
        if (!hasTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true;
        }
        if (hasSpareTireBeenFieldInjected()) {
            subtypeFieldInjectedBeforeSupertypeMethods = true;
        }
        if (hasSpareTireBeenMethodInjected()) {
            subtypeMethodInjectedBeforeSupertypeMethods = true;
        }
        this.methodInjection = methodInjection;
    }

    @Inject static void supertypeStaticMethodInjection(FuelTank methodInjection) {
        if (!Tire.hasBeenStaticFieldInjected()) {
            staticMethodInjectedBeforeStaticFields = true;
        }
        if (SpareTire.hasBeenStaticFieldInjected()) {
            subtypeStaticFieldInjectedBeforeSupertypeStaticMethods = true;
        }
        if (SpareTire.hasBeenStaticMethodInjected()) {
            subtypeStaticMethodInjectedBeforeSupertypeStaticMethods = true;
        }
        staticMethodInjection = methodInjection;
    }

    @Inject private void injectPrivateMethod() {
        if (superPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true;
        }
        superPrivateMethodInjected = true;
    }

    @Inject void injectPackagePrivateMethod() {
        if (superPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true;
        }
        superPackagePrivateMethodInjected = true;
    }

    @Inject protected void injectProtectedMethod() {
        if (superProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true;
        }
        superProtectedMethodInjected = true;
    }

    @Inject
    public void injectPublicMethod() {
        if (superPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true;
        }
        superPublicMethodInjected = true;
    }

    @Inject private void injectPrivateMethodForOverride() {
        subPrivateMethodForOverrideInjected = true;
    }

    @Inject void injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true;
    }

    @Inject protected void injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true;
    }

    @Inject
    public void injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true;
    }

    protected final boolean hasTireBeenFieldInjected() {
        return fieldInjection != NEVER_INJECTED;
    }

    protected boolean hasSpareTireBeenFieldInjected() {
        return false;
    }

    protected final boolean hasTireBeenMethodInjected() {
        return methodInjection != NEVER_INJECTED;
    }

    protected static boolean hasBeenStaticFieldInjected() {
        return staticFieldInjection != NEVER_INJECTED;
    }

    protected static boolean hasBeenStaticMethodInjected() {
        return staticMethodInjection != NEVER_INJECTED;
    }

    protected boolean hasSpareTireBeenMethodInjected() {
        return false;
    }

    public boolean tirePackagePrivateMethod2Injected;

    public boolean getTirePackagePrivateMethod2Injected() {
        return tirePackagePrivateMethod2Injected;
    }

    @Inject void injectPackagePrivateMethod2() {
        tirePackagePrivateMethod2Injected = true;
    }

    public boolean tirePackagePrivateMethod3Injected;

    public boolean getTirePackagePrivateMethod3Injected() {
        return tirePackagePrivateMethod3Injected;
    }

    @Inject void injectPackagePrivateMethod3() {
        tirePackagePrivateMethod3Injected = true;
    }

    public boolean tirePackagePrivateMethod4Injected;

    public boolean getTirePackagePrivateMethod4Injected() {
        return tirePackagePrivateMethod4Injected;
    }

    void injectPackagePrivateMethod4() {
        tirePackagePrivateMethod4Injected = true;
    }
}
