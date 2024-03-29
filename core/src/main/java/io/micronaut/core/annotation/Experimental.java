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
package io.micronaut.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * <p>Annotates a class or method as being experimental and subject to change or removal.</p>
 *
 * <p>Overriding an experimental element will produce a compilation warning. These warnings can be
 * suppressed by setting an annotation processing argument:
 * <code>-Amicronaut.processing.internal.warnings=false</code></p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Experimental {
}
