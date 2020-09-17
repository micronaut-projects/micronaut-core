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
package io.micronaut.http.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.*;

/**
 * <p>An annotation that can be applied to method argument to indicate that the method argument is bound from an HTTP header
 *   This also can be used in conjunction with &#064;Headers to list headers on a client class that will always be applied.</p>
 * <p></p>
 * <p>The following example demonstrates usage at the type level to declare default values to pass in the request when using the {@code Client} annotation:</p>
 * <p></p>
 *
 * <pre class="code">
 * &#064;Header(name="X-Username",value='Freddy'),
 * &#064;Header(name="X-MyParam",value='${foo.bar}')
 * &#064;Client('/users')
 * interface UserClient {
 *
 * }
 * </pre>
 *
 * <p>When declared as a binding annotation the <code>&#064;Header</code> annotation is declared on each parameter to be bound:</p>
 *
 * <pre class="code">
 * &#064;Get('/user')
 * User get(&#064;Header('X-Username') String username, &#064;Header('X-MyParam') String myparam) {
 *    return new User(username, myparam);
 * }
 * </pre>
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE}) // this can be either type or param
@Repeatable(value = Headers.class)
@Bindable
public @interface Header {

    /**
     * If used as a bound parameter, this is the header name. If used on a class level this is value and not the header name.
     * @return The name of the header, otherwise it is inferred from the parameter name
     */
    @AliasFor(annotation = Bindable.class, member = "value")
    String value() default "";

    /**
     * If used on a class level with @Headers this is the header name and value is the value.
     * @return name of header when using with @Headers
     */
    @AliasFor(annotation = Bindable.class, member = "value")
    String name() default "";

    /**
     * @see Bindable#defaultValue()
     * @return The default value
     */
    @AliasFor(annotation = Bindable.class, member = "defaultValue")
    String defaultValue() default "";

}
