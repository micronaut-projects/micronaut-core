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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * A native image feature that lets {@link HttpRequestWrapper#replacesBody(HttpRequest)} look up
 * which class declares the {@code getBody()} of a request wrapper: the public methods of
 * {@link HttpMessageWrapper} and of every reachable {@link HttpRequestWrapper} class, including
 * the classes of applications and libraries, can be queried, not invoked, at run time.
 *
 * @since 5.3.0
 */
@Internal
final class HttpRequestWrapperFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeReflection.registerAllMethods(HttpMessageWrapper.class);
        access.registerSubtypeReachabilityHandler((duringAnalysis, type) -> RuntimeReflection.registerAllMethods(type), HttpRequestWrapper.class);
    }
}
