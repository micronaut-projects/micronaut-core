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
package io.micronaut.context.python.aop;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * A generated class with compiled advised methods: the proxy of the bean binds the advice the
 * compiled methods run their interceptor chains with.
 *
 * @since 5.3.0
 */
@Experimental
@Internal
@NullMarked
public interface StaticAdviceTarget {

    /**
     * The name of the field of the generated class holding the advice.
     */
    String FIELD = "__mn_advice";

    /**
     * The name of the binding method.
     */
    String BIND = "bindStaticAdvice";

    /**
     * Binds the advice of the proxy: from then on the compiled advised methods of this instance
     * run their interceptors in Java before their body.
     *
     * @param advice The advice
     */
    void bindStaticAdvice(StaticAdvice<?> advice);
}
