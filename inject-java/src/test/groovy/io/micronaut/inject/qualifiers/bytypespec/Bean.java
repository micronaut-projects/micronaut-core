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
package io.micronaut.inject.qualifiers.bytypespec;

import io.micronaut.context.annotation.Type;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
class Bean {
    List<Foo> foos;

    public Bean(@Type({One.class,Two.class}) Foo[] foos) {
        this.foos = Arrays.asList(foos);
    }

    public List<Foo> getFoos() {
        return foos;
    }

    public void setFoos(List<Foo> foos) {
        this.foos = foos;
    }
}
