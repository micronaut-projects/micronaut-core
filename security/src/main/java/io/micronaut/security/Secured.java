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
 * Used to mark a route as requiring authorization before execution. When the
 * annotation is placed on a method, it overrides the value of the annotation
 * at the class level, if it exists.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Secured {

    /**
     * The values supplied will be used to secure the target. The values will
     * be compared using "OR". If the authenticated principal contains any of the
     * roles or tokens provided, the principal will be authorized to access
     * the resource.
     *
     * The values can be a role (eg ROLE_ADMIN), or a token.
     * @see io.micronaut.security.rules.SecurityRule#IS_AUTHENTICATED
     * @see io.micronaut.security.rules.SecurityRule#IS_ANONYMOUS
     *
     * @return The list of roles or tokens
     */
    String[] value();
}
