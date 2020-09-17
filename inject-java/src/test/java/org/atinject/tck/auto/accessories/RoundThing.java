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


import javax.inject.Inject;

public class RoundThing {

    private boolean roundThingPackagePrivateMethod2Injected;

    public boolean getRoundThingPackagePrivateMethod2Injected() {
        return roundThingPackagePrivateMethod2Injected;
    }

    @Inject void injectPackagePrivateMethod2() {
        roundThingPackagePrivateMethod2Injected = true;
    }

    private boolean roundThingPackagePrivateMethod3Injected;

    public boolean getRoundThingPackagePrivateMethod3Injected() {
        return roundThingPackagePrivateMethod3Injected;
    }

    @Inject void injectPackagePrivateMethod3() {
        roundThingPackagePrivateMethod3Injected = true;
    }

    private boolean roundThingPackagePrivateMethod4Injected;

    public boolean getRoundThingPackagePrivateMethod4Injected() {
        return roundThingPackagePrivateMethod4Injected;
    }

    @Inject void injectPackagePrivateMethod4() {
        roundThingPackagePrivateMethod4Injected = true;
    }
}
