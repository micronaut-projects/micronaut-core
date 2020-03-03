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
package io.micronaut.inject.value.nullablevalue;

import io.micronaut.context.annotation.Value;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class A {

    public final String nullConstructorArg;
    public final String nonNullConstructorArg;
    public String nullMethodArg;
    public String nonNullMethodArg;
    public @Value("${doesnt.exist}") @Nullable String nullField;
    public @Value("${exists.x}") String nonNullField;


    public A(@Value("${doesnt.exist}") @Nullable String nullConstructorArg,
             @Value("${exists.x}") String nonNullConstructorArg) {
        this.nullConstructorArg = nullConstructorArg;
        this.nonNullConstructorArg = nonNullConstructorArg;
    }

    @Inject
    void injectedMethod(@Value("${doesnt.exist}") @Nullable String nullMethodArg,
                        @Value("${exists.x}") String nonNullMethodArg) {
        this.nullMethodArg = nullMethodArg;
        this.nonNullMethodArg = nonNullMethodArg;
    }
}
