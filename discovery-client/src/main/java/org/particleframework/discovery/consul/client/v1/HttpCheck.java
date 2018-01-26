/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.particleframework.core.convert.value.ConvertibleMultiValues;
import org.particleframework.http.HttpMethod;

import java.net.URL;
import java.util.Optional;

/**
 * A class representing an HTTP check. See https://www.consul.io/api/agent/check.html
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class HttpCheck extends Check {

    private final URL url;
    private HttpMethod method;
    private boolean TLSSkipVerify = false;
    private ConvertibleMultiValues<String> headers = ConvertibleMultiValues.empty();

    @JsonCreator
    public HttpCheck(@JsonProperty("Name") String name, @JsonProperty("HTTP") URL url) {
        super(name);
        this.url = url;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#http
     * @return The HTTP URL to check
     */
    public URL getHTTP() {
        return url;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#method
     * @return The HTTP method to use for the check
     */
    public Optional<HttpMethod> getMethod() {
        return Optional.ofNullable(method);
    }

    /**
     * See https://www.consul.io/api/agent/service.html#header
     *
     * @return The HTTP headers to use for the check
     */
    public ConvertibleMultiValues<String> getHeader() {
        return headers;
    }

    public void setHeaders(ConvertibleMultiValues<String> headers) {
        if (headers == null) {
            this.headers = ConvertibleMultiValues.empty();
        }
        else {
            this.headers = headers;
        }
    }

    public boolean isTLSSkipVerify() {
        return TLSSkipVerify;
    }

    public void setTLSSkipVerify(boolean TLSSkipVerify) {
        this.TLSSkipVerify = TLSSkipVerify;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public HttpCheck headers(ConvertibleMultiValues<String> headers) {
        setHeaders(headers);
        return this;
    }

    public HttpCheck TLSSkipVerify(boolean TLSSkipVerify) {
        this.TLSSkipVerify = TLSSkipVerify;
        return this;
    }

    public HttpCheck method(HttpMethod method) {
        this.method = method;
        return this;
    }

    @Override
    public HttpCheck id(String ID) {
        return (HttpCheck) super.id(ID);
    }

    @Override
    public HttpCheck status(HealthStatus status) {
        return (HttpCheck) super.status(status);
    }

    @Override
    public HttpCheck notes(String notes) {
        return (HttpCheck) super.notes(notes);
    }
}
