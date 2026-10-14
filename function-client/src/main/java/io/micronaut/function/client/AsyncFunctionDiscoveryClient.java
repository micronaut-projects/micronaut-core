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
package io.micronaut.function.client;

import io.micronaut.core.annotation.Experimental;

import java.util.concurrent.CompletionStage;

/**
 * The {@link CompletionStage} counterpart of a {@link FunctionDiscoveryClient}, which discovers
 * functions, either remote or local, without a publisher.
 *
 * <p>An implementation returns a new stage for each call, which a caller may cancel. The
 * framework never cancels a stage it did not create: it ignores its result instead.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncFunctionDiscoveryClient {

    /**
     * Discover the function for the given function name.
     *
     * @param functionName The function name
     * @return A {@link CompletionStage} completed with the {@link FunctionDefinition}, or with a {@link io.micronaut.function.client.exceptions.FunctionNotFoundException} if no function is found
     */
    CompletionStage<FunctionDefinition> getFunction(String functionName);
}
