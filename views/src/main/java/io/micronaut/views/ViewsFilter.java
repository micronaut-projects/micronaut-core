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

package io.micronaut.views;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.Writable;
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
import io.micronaut.web.router.qualifier.ProducesMediaTypeQualifier;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * Templates Filter.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(beans = ViewsRenderer.class)
@Filter("/**")
public class ViewsFilter extends OncePerRequestHttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ViewsFilter.class);

    protected final BeanLocator beanLocator;

    /**
     * Constructor.
     * @param beanLocator The bean locator
     */
    public ViewsFilter(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request,
                                                             ServerFilterChain chain) {

        Optional<MethodExecutionHandle> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH,
                MethodExecutionHandle.class);
        if (routeMatch.isPresent()) {
            MethodExecutionHandle route = routeMatch.get();
            MediaType type = route.getValue(Produces.class, MediaType.class)
                    .orElse(MediaType.TEXT_HTML_TYPE);
            Optional<ViewsRenderer> optionalViewsRenderer = beanLocator.findBean(ViewsRenderer.class,
                    new ProducesMediaTypeQualifier<>(type));

            if (!optionalViewsRenderer.isPresent()) {
                return chain.proceed(request);
            }

            ViewsRenderer viewsRenderer = optionalViewsRenderer.get();

            return Flowable.fromPublisher(chain.proceed(request))
                    .switchMap(response -> {
                        Object body = response.body();
                        Optional<String> optionalView = resolveView(route, body);

                        if (!optionalView.isPresent()) {
                            return Flowable.just(response);
                        }

                        String view = optionalView.get();

                        if (!viewsRenderer.exists(view)) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("view {} not found ", view);
                            }
                            response.status(HttpStatus.NOT_FOUND);
                            return Flowable.just(response);
                        }

                        Object model = resolveModel(body);
                        Writable writable = viewsRenderer.render(view, model);
                        response.contentType(type);
                        ((MutableHttpResponse<Object>) response).body(writable);
                        return Flowable.just(response);
                    });
        }
        return chain.proceed(request);
    }

    /**
     *
     * @param responseBody Response body
     * @return the model to be rendered
     */
    protected Object resolveModel(Object responseBody) {
        if (responseBody instanceof ModelAndView) {
            return ((ModelAndView) responseBody).getModel();
        }
        return responseBody;
    }

    /**
     *
     * @param route Request route
     * @param responseBody Response body
     * @return view name to be rendered
     */
    protected Optional<String> resolveView(MethodExecutionHandle route, Object responseBody) {
        Optional optionalViewName = route.getValue(View.class);
        if (optionalViewName.isPresent()) {
            return Optional.of((String) optionalViewName.get());
        } else if (responseBody instanceof ModelAndView) {
            return Optional.of(((ModelAndView) responseBody).getView());
        }
        return Optional.empty();
    }

    /**
     * Store the writable where the view will be written into and the response.
     */
    class WritableAndResponse {
        final Writable writable;
        final MutableHttpResponse<?> response;

        /**
         * Constructor.
         *
         * @param writable A writable where the view will be written to.
         * @param response The mutable HTTP response
         */
        WritableAndResponse(Writable writable, MutableHttpResponse<?> response) {
            this.writable = writable;
            this.response = response;
        }
    }

}
