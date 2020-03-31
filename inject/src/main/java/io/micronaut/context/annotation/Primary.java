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
package io.micronaut.context.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * <p>A {@link Qualifier} that indicates that this bean is the primary bean that should be selected in the case of
 * multiple possible interface implementations.</p>
 *
 * <p>Note that if multiple primary beans are found then a {@link io.micronaut.context.exceptions.NonUniqueBeanException} can still occur.</p>
 *
 * @author Graeme Rocher
 * @see Qualifier @Qualifier
 */
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface Primary {
    /**
     * The simple name of this annotation.
     */
    String SIMPLE_NAME = Primary.class.getSimpleName();
}
