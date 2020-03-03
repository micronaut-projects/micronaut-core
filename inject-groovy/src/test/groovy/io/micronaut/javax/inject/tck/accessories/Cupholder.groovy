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
import io.micronaut.javax.inject.tck.Seat

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Cupholder {

    public final Provider<Seat> seatProvider

    @Inject
    @PackageScope
    Cupholder(Provider<Seat> seatProvider) {
        this.seatProvider = seatProvider
    }
}
