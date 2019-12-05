/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.validation;

import groovy.lang.Singleton;

import javax.validation.Valid;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Validated
public class Foo {

    public String testMe(@Digits(integer = 3, fraction = 2) String number) {
        return '$' + number;
    }

    @Valid
    @NotNull
    public String notNull() {
        return null;
    }
}