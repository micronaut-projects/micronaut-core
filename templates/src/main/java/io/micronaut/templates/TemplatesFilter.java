/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.templates;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.inject.MethodExecutionHandle;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Templates Filter.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(beans = TemplateRenderer.class)
@Filter("/**")
public class TemplatesFilter extends OncePerRequestHttpServerFilter {

    protected final Collection<TemplateRenderer> templateRenderers;

    /**
     *
     * @param templateRenderers Collection of {@link TemplateRenderer} beans.
     */
    public TemplatesFilter(Collection<TemplateRenderer> templateRenderers) {
        this.templateRenderers = templateRenderers;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

        Optional<MethodExecutionHandle> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, MethodExecutionHandle.class);
        if (routeMatch.isPresent()) {
            MethodExecutionHandle route = routeMatch.get();
            Optional optionalTemplateName = route.getValue(Template.class);
            if (!optionalTemplateName.isPresent()) {
                return chain.proceed(request);
            }
            String view = (String) optionalTemplateName.get();
            Flowable<ViewRenderedAndResponse> responseFlowable = Flowable.fromPublisher(chain.proceed(request))
                    .switchMap(response -> {
                        Object body = response.body();
                        return Flowable.fromPublisher(Publishers.fromCompletableFuture(
                                CompletableFuture.completedFuture(
                                        new ViewRenderedAndResponse(templateRenderers.stream()
                                                .map(templateRenderer -> templateRenderer.render(view, body))
                                                .filter(Optional::isPresent)
                                                .findFirst()
                                                .orElse(Optional.empty()), response))));
                    });
            return responseFlowable.map(viewRenderedAndResponse -> {
                Optional<String> templateRendered = viewRenderedAndResponse.templateRendered;
                MutableHttpResponse<Object> response = (MutableHttpResponse<Object>) viewRenderedAndResponse.response;
                if (!templateRendered.isPresent()) {
                    response.status(HttpStatus.NOT_FOUND);
                }

                response.body(templateRendered);
                MediaType contentType = route.getValue(Produces.class, MediaType.class).orElse(MediaType.TEXT_HTML_TYPE);
                response.contentType(contentType);
                return response;
            });

        }
        return chain.proceed(request);
    }

    /**
     * Store the view rendered and the response.
     */
    class ViewRenderedAndResponse {
        final Optional<String> templateRendered;
        final MutableHttpResponse<?> response;

        /**
         * Constructor.
         *
         * @param templateRendered The Template Rendered
         * @param response The mutable HTTP response
         */
        ViewRenderedAndResponse(Optional<String> templateRendered, MutableHttpResponse<?> response) {
            this.templateRendered = templateRendered;
            this.response = response;
        }
    }

}
