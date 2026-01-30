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
package io.micronaut.context.bean.definition.builder;

/**
 * Builder abstraction for proxy-oriented bean definitions.
 *
 * @param <M> The method element type
 * @param <R> The builder result
 * @author Denis Stepanov
 * @since 5.0
 */
public interface ProxyBuilder<C, M, R> extends Builder<R> {

    /**
     * Adds an interface to be implemented by the proxy.
     *
     * @param interfaceElement          The interface element
     */
    ProxyBuilder<C, M, R> implementInterface(C interfaceElement);

    /**
     * Adds a proxied method that delegates to the target.
     *
     * @param methodElement The method to proxy
     */
    ProxyBuilder<C, M, R> addProxyMethod(M methodElement);

    /**
     * Adds an introduction method implemented directly by the proxy.
     *
     * @param methodElement The method to introduce
     */
    ProxyBuilder<C, M, R> addIntroductionMethod(M methodElement);

    /**
     * Adds a method that should participate in around advice.
     *
     * @param methodElement The method element
     */
    ProxyBuilder<C, M, R> addAroundMethod(M methodElement);

}
