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
package io.micronaut.http;

import java.security.Principal;

/**
 * Common HTTP attributes.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated In order to make future optimizations possible, standard attributes should be
 * accessed through their static method accessors (such as those in {@link BasicHttpAttributes})
 * instead of directly through {@link io.micronaut.core.attr.AttributeHolder}.
 */
@Deprecated(forRemoval = true, since = "4.8.0")
public enum HttpAttributes implements CharSequence {

    /**
     * Attribute used to store the {@link java.security.Principal}.
     *
     * @deprecated Use {@link HttpRequest#getUserPrincipal()} and {@link HttpRequest#setUserPrincipal(Principal)}
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    PRINCIPAL("micronaut.AUTHENTICATION"),

    /**
     * Attribute used to store any exception that may have occurred during request processing.
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    ERROR(Constants.PREFIX + ".error"),

    /**
     * Attribute used to store the object that represents the Route match.
     *
     * @deprecated Please use the accessors in RouteAttributes
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    ROUTE_MATCH(Constants.PREFIX + ".route.match"),

    /**
     * Attribute used to store the object that represents the Route.
     *
     * @deprecated Please use the accessors in RouteAttributes
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    ROUTE_INFO(Constants.PREFIX + ".route.info"),

    /**
     * Attribute used to store the URI template defined by the route.
     *
     * @deprecated Use {@link BasicHttpAttributes#getUriTemplate} instead
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    URI_TEMPLATE(Constants.PREFIX + ".route.template"),

    /**
     * Attribute used to store the HTTP method name, if required within the response.
     *
     * @deprecated No replacement. Use your own attribute if necessary
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    METHOD_NAME(Constants.PREFIX + ".method.name"),

    /**
     * Attribute used to store the service ID a client request is being sent to. Used for tracing purposes.
     *
     * @deprecated Use {@link BasicHttpAttributes#getServiceId}
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    SERVICE_ID(Constants.PREFIX + ".serviceId"),

    /**
     * Attribute used to store the MediaTypeCodec. Used to override the registered codec per-request.
     *
     * @deprecated Unused
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    MEDIA_TYPE_CODEC(Constants.PREFIX + ".mediaType.codec"),

    /**
     * Attribute used to store the MethodInvocationContext by declarative client.
     *
     * @deprecated Please use accessors in ClientAttributes instead
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    INVOCATION_CONTEXT(Constants.PREFIX + ".invocationContext"),

    /**
     * Attribute used to store the cause of an error response.
     *
     * @deprecated Please use the accessors in RouteAttributes
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    EXCEPTION(Constants.PREFIX + ".exception"),

    /**
     * Attribute used to store a client Certificate (mutual authentication).
     *
     * @deprecated Use {@link HttpRequest#getCertificate()} instead
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    X509_CERTIFICATE("javax.servlet.request.X509Certificate"),

    /**
     * Attribute used to store Available HTTP methods on the OPTIONS request.
     * @deprecated Not used anymore
     */
    @Deprecated(forRemoval = true, since = "4.7")
    AVAILABLE_HTTP_METHODS(Constants.PREFIX + ".route.availableHttpMethods"),

    /**
     * The message body writer.
     *
     * @deprecated Use accessors in {@link HttpMessage} instead
     */
    @Deprecated(forRemoval = true, since = "4.8.0")
    MESSAGE_BODY_WRITER(Constants.PREFIX + ".messageBodyWriter"),

    /**
     * Body that was discarded because this is a HEAD response.
     */
    HEAD_BODY(Constants.PREFIX + ".headBody");

    private final String name;

    /**
     * @param name The name
     */
    HttpAttributes(String name) {
        this.name = name;
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Constants.
     */
    private static class Constants {
        public static final String PREFIX = "micronaut.http";
    }
}
