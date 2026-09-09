/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.annotation.NonNull;

/**
 * Represents a Kotlin method or constructor.
 *
 * <p>Implementing this interface signals that defaulted parameters follow <i>Kotlin's</i> calling
 * convention, and tells generated code where to find the synthetic {@code $default} overload that
 * convention relies on. See {@link KotlinParameterElement}.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public interface KotlinMethodElement extends MethodElement {

    /**
     * The name of the type declaring the synthetic {@code $default} method that applies this
     * method's Kotlin default arguments.
     *
     * <p>For a method declared by a class this is the declaring class itself, but a method
     * declared by an interface has its {@code $default} overload emitted either on the interface
     * or on the interface's {@code DefaultImpls} class, depending on the {@code -jvm-default} mode
     * the interface was compiled with.</p>
     *
     * @return The type name, in binary form (nested types separated by {@code $})
     */
    @NonNull
    String getKotlinDefaultsTypeName();

}
