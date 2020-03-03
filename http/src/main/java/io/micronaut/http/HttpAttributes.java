/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

/**
 * Common HTTP attributes.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum HttpAttributes implements CharSequence {

    /**
     * Attribute used to store the {@link java.security.Principal}.
     */
    PRINCIPAL("micronaut.AUTHENTICATION"),

    /**
     * Attribute used to store any exception that may have occurred during request processing.
     */
    ERROR(Constants.PREFIX + ".error"),

    /**
     * Attribute used to store the object that represents the Route.
     */
    ROUTE(Constants.PREFIX + ".route"),

    /**
     * Attribute used to store the object that represents the Route match.
     */
    ROUTE_MATCH(Constants.PREFIX + ".route.match"),

    /**
     * Attribute used to store the URI template defined by the route.
     */
    URI_TEMPLATE(Constants.PREFIX + ".route.template"),

    /**
     * Attribute used to store the HTTP method name, if required within the response.
     */
    METHOD_NAME(Constants.PREFIX + ".method.name"),

    /**
     * Attribute used to store the service ID a client request is being sent to. Used for tracing purposes.
     */
    SERVICE_ID(Constants.PREFIX + ".serviceId"),

    /**
     * Attribute used to store the MediaTypeCodec. Used to override the registered codec per-request.
     */
    MEDIA_TYPE_CODEC(Constants.PREFIX + ".mediaType.codec"),

    /**
     * Attribute used to store the MethodInvocationContext by declarative client.
     */
    INVOCATION_CONTEXT(Constants.PREFIX + ".invocationContext"),

    /**
     * Attribute used to store a client Certificate (mutual authentication).
     */
    X509_CERTIFICATE("javax.servlet.request.X509Certificate");

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
