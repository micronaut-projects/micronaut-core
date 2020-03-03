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
package org.atinject.tck.auto.accessories


import javax.inject.Inject

open class RoundThing {

    var roundThingPackagePrivateMethod2Injected: Boolean = false
        private set

    var roundThingPackagePrivateMethod3Injected: Boolean = false
        private set

    var roundThingPackagePrivateMethod4Injected: Boolean = false
        private set

    @Inject open internal fun injectPackagePrivateMethod2() {
        roundThingPackagePrivateMethod2Injected = true
    }

    @Inject open internal fun injectPackagePrivateMethod3() {
        roundThingPackagePrivateMethod3Injected = true
    }

    @Inject open internal fun injectPackagePrivateMethod4() {
        roundThingPackagePrivateMethod4Injected = true
    }
}
