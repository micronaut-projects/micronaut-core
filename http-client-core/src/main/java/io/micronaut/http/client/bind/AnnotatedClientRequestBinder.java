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
package io.micronaut.http.client.bind;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MutableHttpRequest;

import java.lang.annotation.Annotation;

/**
 * A binder that binds to a {@link MutableHttpRequest}.
 * This binder is used when the annotation is applied to the whole method.
 * In case of binder for arguments use {@link ClientArgumentRequestBinder}/
 *
 * @param <A> - the annotation type that this binder is applied with
 *
 * @author Andriy Dmytruk
 * @since 3.0.0
 */
@Experimental
@BootstrapContextCompatible
@Indexed(AnnotatedClientRequestBinder.class)
public interface AnnotatedClientRequestBinder<A extends Annotation> extends ClientRequestBinder {

    /**
     * Modify the request with the annotation that this binder is applied to.
     * The URI cannot be changed. The query parameters from the uriContext remain in the resulting request.
     *
     * @param context The context of method invocation
     * @param uriContext The URI context
     * @param request The request
     */
    void bind(@NonNull MethodInvocationContext<Object, Object> context,
              @NonNull ClientRequestUriContext uriContext,
              @NonNull MutableHttpRequest<?> request);

    /**
     * @return The annotation type.
     */
    @NonNull
    Class<A> getAnnotationType();
}
