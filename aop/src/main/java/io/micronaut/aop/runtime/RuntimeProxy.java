/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.aop.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * The annotation used to declare a runtime proxy.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Documented
@Retention(SOURCE)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
public @interface RuntimeProxy {

    /**
     * The runtime proxy creator bean class that should be used at runtime to create a proxy.
     *
     * @return The runtime proxy creator bean class
     */
    Class<? extends RuntimeProxyCreator> value();

    /**
     * <p>By default Micronaut will compile subclasses of the target class and call super.foo(..) to invoke the original method since
     * this is more efficient and allows proxied methods to work for calls from within the class.</p>
     *
     * <p>However certain cases it may be useful to be able to to instead proxy all public methods of the original implementation.
     * By setting the value here to <code>true</code> the {@link io.micronaut.aop.Interceptor} can specify that it requires proxying of the class</p>
     *
     * <p>Generated subclasses will implement {@link io.micronaut.aop.InterceptedProxy} if this attribute is set to true</p>
     *
     * @return True if the original implementation should be proxied. Defaults to false.
     */
    boolean proxyTarget() default false;
}
