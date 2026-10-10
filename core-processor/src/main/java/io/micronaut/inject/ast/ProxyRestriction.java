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

/**
 * Why a type cannot be extended or implemented by a build time generated proxy, as
 * {@link ClassElement#findProxyRestriction()} reports it.
 *
 * <p>Only the type itself is described. The constructors a proxy is created through and the methods it overrides
 * are not, since what a proxy needs of them depends on the kind of proxy.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public enum ProxyRestriction {

    /**
     * A primitive type, which has no subtypes. An array of primitives is an {@link #ARRAY}.
     */
    PRIMITIVE,

    /**
     * An array type, which has no subtypes.
     */
    ARRAY,

    /**
     * An enum, whose only subclasses are the bodies of its own constants.
     */
    ENUM,

    /**
     * A final class, a record among them.
     */
    FINAL,

    /**
     * A sealed type, which permits only the subtypes it lists.
     */
    SEALED
}
