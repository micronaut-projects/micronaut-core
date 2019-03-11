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
package io.micronaut.docs.rejection;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.handlers.HttpStatusCodeRejectionHandler;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

@Requires(property = "spec.name", value = "rejection-handler")
//tag::clazz[]
@Singleton
@Replaces(HttpStatusCodeRejectionHandler.class)
public class MyRejectionHandler extends HttpStatusCodeRejectionHandler {

    @Override
    public Publisher<MutableHttpResponse<?>> reject(HttpRequest<?> request, boolean forbidden) {
        //Let the HttpStatusCodeRejectionHandler create the initial request
        //then add a header
        return Flowable.fromPublisher(super.reject(request, forbidden))
                .map(response -> response.header("X-Reason", "Example Header"));
    }
}
//end::clazz[]