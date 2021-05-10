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

/**
 * Created by graemerocher on 12/05/2017.
 */

import jakarta.inject.Singleton

@Singleton
class Seat {

    private final io.micronaut.jakarta.inject.tck.accessories.Cupholder cupholder

    @Inject
    @PackageScope Seat(io.micronaut.jakarta.inject.tck.accessories.Cupholder cupholder) {
        this.cupholder = cupholder
    }

    io.micronaut.jakarta.inject.tck.accessories.Cupholder getCupholder() {
        return cupholder
    }
}
