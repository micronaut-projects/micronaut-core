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
package io.micronaut.docs.qualifiers.annotation;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class V8Engine implements Engine { // <2>
    private int cylinders = 8;

    @Override
    public int getCylinders() {
        return cylinders;
    }

    @Override
    public String start() {
        return "Starting V8";
    }
}
// end::class[]
