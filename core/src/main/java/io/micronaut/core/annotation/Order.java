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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for objects that are ordered.
 *
 * <p>Order in a Micronaut application is important in multiple aspects including but note limited to:</p>
 *
 * <ul>
 *     <li>Controlling bean selection and prioritization</li>
 *     <li>Ordering injected {@link java.util.List} collections.</li>
 *     <li>Ordering AOP method interceptors</li>
 *     <li>Ordering HTTP filters</li>
 * </ul>
 *
 * <p>This annotation can be used to control the order by specifying a numerical value
 * that sorts components in the desired order</p>
 *
 * @author Sean Carroll
 * @since 2.0
 * @see io.micronaut.core.order.Ordered
 * @see io.micronaut.core.order.Ordered#HIGHEST_PRECEDENCE
 * @see io.micronaut.core.order.Ordered#LOWEST_PRECEDENCE
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Order {

    /**
     * The order value.
     *
     * Defaults to zero (no order).
     *
     * @return the order
     */
    int value() default 0;

}
