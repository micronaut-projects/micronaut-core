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
package io.micronaut.inject.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class NamedFunctionBean {

    private final NamedFunction inputFromConstructor;
    private final NamedFunction outputFromConstructor;
    @Inject
    @Named("INPUT")
    private NamedFunction privateFieldInput;
    @Inject
    @Named("OUTPUT")
    private NamedFunction privateFieldOutput;
    public NamedFunctionBean(
            @Named("INPUT") NamedFunction inputFromConstructor,
            @Named("OUTPUT") NamedFunction outputFromConstructor) {
        this.inputFromConstructor = inputFromConstructor;
        this.outputFromConstructor = outputFromConstructor;
    }

    public NamedFunction getInputFromConstructor() {
        return inputFromConstructor;
    }

    public NamedFunction getOutputFromConstructor() {
        return outputFromConstructor;
    }

    public NamedFunction getPrivateFieldInput() {
        return privateFieldInput;
    }

    public NamedFunction getPrivateFieldOutput() {
        return privateFieldOutput;
    }
}
