/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which allows users to define {@link io.micronaut.security.rules.SecurityRule} for HTTP {@link io.micronaut.http.annotation.Controller} or HTTP {@link io.micronaut.http.annotation.Controller}actions.
 * @author James Kleeh
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Secured {

    /**
     * @return An Security expression such as {@link io.micronaut.security.rules.SecurityRule#IS_ANONYMOUS} or {@link io.micronaut.security.rules.SecurityRule#IS_AUTHENTICATED}
     */
    String[] value();
}
