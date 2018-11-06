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

package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
import io.micronaut.http.HttpStatus;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * A publisher that will deal with the web filter metrics for success and error conditions.
 *
 * @author Christian Oestreich
 * @author graemerocher
 * @since 1.0
 * @param <T> The response type
 */
@SuppressWarnings("PublisherImplementation")
public class WebMetricsPublisher<T extends HttpResponse<?>> implements Publisher<T> {

    /**
     * Constant used to define whether web metrics are enabled or not.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".web.enabled";
    public static final String METRIC_HTTP_SERVER_REQUESTS = "http.server.requests";
    public static final String METRIC_HTTP_CLIENT_REQUESTS = "http.client.requests";

    private static final Tag URI_NOT_FOUND = Tag.of("uri", "NOT_FOUND");
    private static final Tag URI_REDIRECTION = Tag.of("uri", "REDIRECTION");
    private static final String UNKNOWN = "UNKNOWN";
    private static final String METHOD = "method";
    private static final String STATUS = "status";
    private static final String URI = "uri";
    private static final String EXCEPTION = "exception";

    private final Publisher<T> publisher;
    private final MeterRegistry meterRegistry;
    private final String requestPath;
    private final long start;
    private final String httpMethod;
    private final String metricName;

    /**
     * Publisher constructor.
     *
     * @param publisher     The original publisher
     * @param meterRegistry MeterRegistry bean
     * @param requestPath   The request path
     * @param start         The start time of the request
     * @param httpMethod    The http method name used
     * @param isServer      Whether the metric relates to the server or the client
     */
    WebMetricsPublisher(
            Publisher<T> publisher,
            MeterRegistry meterRegistry,
            String requestPath,
            long start,
            String httpMethod,
            boolean isServer) {
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
        this.requestPath = requestPath;
        this.start = start;
        this.httpMethod = httpMethod;
        this.metricName = isServer ? METRIC_HTTP_SERVER_REQUESTS : METRIC_HTTP_CLIENT_REQUESTS;
    }

    /**
     * Publisher constructor.
     *
     * @param publisher     The original publisher
     * @param meterRegistry MeterRegistry bean
     * @param requestPath   The request path
     * @param start         The start time of the request
     * @param httpMethod    The http method name used
     */
    WebMetricsPublisher(
            Publisher<T> publisher,
            MeterRegistry meterRegistry,
            String requestPath,
            long start,
            String httpMethod) {
        this(publisher, meterRegistry, requestPath, start, httpMethod, true);
    }

    /**
     * The subscribe method that will be called for publisher.
     *
     * @param actual the original subscription
     */
    @SuppressWarnings("SubscriberImplementation")
    @Override
    public void subscribe(Subscriber<? super T> actual) {

        publisher.subscribe(new Subscriber<T>() {
            /**
             * Subscription handler.
             * @param subscription the subscription
             */
            @Override
            public void onSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            /**
             * Request success handler.
             * @param httpResponse the http response
             */
            @Override
            public void onNext(T httpResponse) {
                success(httpResponse, start, httpMethod, requestPath);
                actual.onNext(httpResponse);
            }

            /**
             * Request error handler.
             */
            @Override
            public void onError(Throwable throwable) {
                error(start, httpMethod, requestPath, throwable);
                actual.onError(throwable);
            }

            /**
             * Request complete handler.
             */
            @Override
            public void onComplete() {
                actual.onComplete();
            }
        });
    }

    /**
     * Get the tags for the metrics based on request shape.
     *
     * @param httpResponse The http response
     * @param httpMethod   The name of the http method (GET, POST, etc)
     * @param requestPath  The request path (/foo, /foo/bar, etc)
     * @param throwable    The throwable (optional)
     * @return A list of Tag objects
     */
    private static List<Tag> getTags(HttpResponse httpResponse,
                                     String httpMethod,
                                     String requestPath,
                                     Throwable throwable) {
        return Stream
                .of(method(httpMethod), status(httpResponse), uri(httpResponse, requestPath), exception(throwable))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get a tag with the http method name.
     *
     * @param httpMethod The name of the http method.
     * @return Tag of method
     */
    private static Tag method(String httpMethod) {
        Tag tag = null;
        if (httpMethod != null) {
            tag = Tag.of(METHOD, httpMethod);
        }
        return tag;
    }

    /**
     * Get a tag with the http status value.
     *
     * @param httpResponse the http response
     * @return Tag of status
     */
    private static Tag status(HttpResponse httpResponse) {
        if (httpResponse == null) {
            return Tag.of(STATUS, "500");
        }

        HttpStatus status = httpResponse.status();
        if (status == null) {
            status = HttpStatus.OK;
        }
        return Tag.of(STATUS, String.valueOf(status.getCode()));
    }

    /**
     * Get a tag with the uri.
     *
     * @param httpResponse the http response
     * @param path         the path of the request
     * @return Tag of uri
     */
    private static Tag uri(HttpResponse httpResponse, String path) {
        if (httpResponse != null) {
            HttpStatus status = httpResponse.getStatus();
            if (status != null && status.getCode() >= 300 && status.getCode() < 400) {
                return URI_REDIRECTION;
            }
            if (status != null && status.equals(HttpStatus.NOT_FOUND)) {
                return URI_NOT_FOUND;
            }
        }
        return Tag.of(URI, sanitizePath(path));
    }

    /**
     * Get a tag with the throwable.
     *
     * @param throwable a throwable exception
     * @return Tag of exception class name
     */
    private static Tag exception(Throwable throwable) {
        if (throwable != null) {
            return Tag.of(EXCEPTION, throwable.getClass().getSimpleName());
        }
        return Tag.of(EXCEPTION, "none");
    }

    /**
     * Sanitize the uri path for double slashes and ending slashes.
     *
     * @param path the uri of the request
     * @return sanitized string
     */
    private static String sanitizePath(String path) {
        if (!StringUtils.isEmpty(path)) {
            return path
                    .replaceAll("//+", "/")
                    .replaceAll("/$", "");
        }

        return path != null ? (path.isEmpty() ? "root" : path) : UNKNOWN;
    }

    /**
     * Method to register the success timer for a web request.
     *
     * @param httpResponse the http response
     * @param start        the start time of the request
     * @param httpMethod   the name of the http method (GET, POST, etc)
     * @param requestPath  the uri of the reuqest
     */
    private void success(HttpResponse httpResponse, long start, String httpMethod, String requestPath) {
        Iterable<Tag> tags = getTags(httpResponse, httpMethod, requestPath, null);
        this.meterRegistry.timer(metricName, tags)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    /**
     * Method to register the error timer for a web request when an exception occurs.
     *
     * @param start       the start time of the request
     * @param httpMethod  the name of the http method (GET, POST, etc)
     * @param requestPath the uri of the reuqest
     * @param throwable   exception that occurred
     */
    private void error(long start, String httpMethod, String requestPath, Throwable throwable) {
        HttpResponse response = null;
        if (throwable instanceof HttpResponseProvider) {
            response = ((HttpResponseProvider) throwable).getResponse();
        }
        Iterable<Tag> tags = getTags(response, httpMethod, requestPath, throwable);
        this.meterRegistry.timer(metricName, tags)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
}
