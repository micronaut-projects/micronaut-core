/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.aop;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>Annotation stereotype to applied to other annotations to indicate the annotation provides Around advice.</p>
 *
 * <p>Around advice decorates a method invocation such that the method can be intercepted via a {@link MethodInterceptor}</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 *  {@literal @}Around
 *  {@literal @}Type(ExampleInterceptor.class)
 *  {@literal @}Documented
 *  {@literal @}Retention(RUNTIME)
 *   public @interface Example {
 *   }
 * </code></pre>
 *
 * <p>Note that the annotation MUST be {@link java.lang.annotation.RetentionPolicy#RUNTIME} and the specified {@link io.micronaut.context.annotation.Type} must implement {@link MethodInterceptor}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface Around {

    /**
     * <p>By default Micronaut will compile subclasses of the target class and call super.foo(..) to invoke the original method since
     * this is more efficient and allows proxied methods to work for calls from within the class.</p>
     *
     * <p>However certain cases it may be useful to be able to to instead proxy all public methods of the original implementation.
     * By setting the value here to <code>true</code> the {@link Interceptor} can specify that it requires proxying of the class</p>
     *
     * <p>Generated subclasses will implement {@link InterceptedProxy} if this attribute is set to true</p>
     *
     * @return True if the original implementation should be proxied. Defaults to false.
     * @see InterceptedProxy
     */
    boolean proxyTarget() default false;

    /**
     * <p>If {@link #proxyTarget()} is set to <code>true</code> then one can optionally set the of <code>hotswap</code> to true
     * in which case the proxy will implement the {@link HotSwappableInterceptedProxy} interface.</p>
     *
     * @return True if the proxy should allow hotswap
     * @see HotSwappableInterceptedProxy
     */
    boolean hotswap() default false;

    /**
     * <p>By default Micronaut will initialize the proxy target eagerly when the proxy is created. This is better for performance, but some use
     * cases may require the bean to be resolved lazily (for example for resolving the bean from a custom scope).</p>
     *
     * <p>If {@link #proxyTarget()} is set to <code>true</code> then one can optionally set the of <code>lazy</code> to true</p>
     *
     * @return True if the proxy target should be resolved lazily
     */
    boolean lazy() default false;
}
