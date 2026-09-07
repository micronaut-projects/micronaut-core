/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;

/**
 * Represents a Kotlin parameter to a method or constructor.
 *
 * <p>Implementing this interface additionally signals that defaulted parameters follow
 * <i>Kotlin's</i> calling convention: a defaults bitmask plus the synthetic {@code $default}
 * overload. It is therefore not a general marker for optional parameters — use
 * {@link ParameterElement#hasDefault()} for that, and
 * {@link DefaultValueProvidingParameterElement} for languages that evaluate the default in the
 * caller.</p>
 *
 * @author Denis Stepanov
 * @since 4.1.0
 */
@Experimental
public interface KotlinParameterElement extends ParameterElement {

    /**
     * @return True if the parameter has a default value
     */
    @Override
    boolean hasDefault();

}
