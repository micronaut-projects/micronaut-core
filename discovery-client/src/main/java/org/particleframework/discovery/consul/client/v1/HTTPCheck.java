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
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.ConvertibleMultiValues;
import org.particleframework.http.HttpMethod;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A class representing an HTTP check. See https://www.consul.io/api/agent/check.html
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class HTTPCheck extends Check {

    private Duration interval;
    private final URL url;
    private HttpMethod method;
    private boolean TLSSkipVerify = false;
    private ConvertibleMultiValues<String> headers = ConvertibleMultiValues.empty();

    @JsonCreator
    public HTTPCheck(@JsonProperty("Name") String name, @JsonProperty("HTTP") URL url) {
        super(name);
        this.url = url;
    }

    public HTTPCheck(@JsonProperty("HTTP") URL url) {
        this.url = url;
    }

    /**
     * @return The interval as a {@link Duration}
     */
    public Duration interval() {
        return this.interval;
    }

    void setInterval(String interval) {
        this.interval = ConversionService.SHARED.convert(interval, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid Duration Specified"));
    }

    public HTTPCheck interval(Duration interval) {
        if(interval != null) {
            this.interval = interval;
        }
        return this;
    }

    public HTTPCheck interval(String interval) {
        this.interval = ConversionService.SHARED.convert(interval, Duration.class).orElseThrow(()-> new IllegalArgumentException("Invalid Duration Specified"));
        return this;
    }

    /**
     * @return The check interval
     */
    public Optional<String> getInterval() {
        if(interval != null) {
            return Optional.of(interval.getSeconds() + "s");
        }
        return Optional.empty();
    }

    /**
     * See https://www.consul.io/api/agent/service.html#http
     * @return The HTTP URL to check
     */
    @JsonProperty("HTTP")
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

    @JsonProperty("Header")
    public void setHeaders(Map<CharSequence, List<String>> headers) {
        if (headers == null) {
            this.headers = ConvertibleMultiValues.empty();
        }
        else {
            this.headers = ConvertibleMultiValues.of(headers);
        }
    }

    @JsonProperty("TLSSkipVerify")
    public boolean isTLSSkipVerify() {
        return TLSSkipVerify;
    }

    @JsonProperty("TLSSkipVerify")
    public void setTLSSkipVerify(boolean TLSSkipVerify) {
        this.TLSSkipVerify = TLSSkipVerify;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public HTTPCheck headers(ConvertibleMultiValues<String> headers) {
        if(headers != null) {
            this.headers = headers;
        }
        return this;
    }

    public HTTPCheck tlsSkipVerify(boolean TLSSkipVerify) {
        this.TLSSkipVerify = TLSSkipVerify;
        return this;
    }

    public HTTPCheck method(HttpMethod method) {
        this.method = method;
        return this;
    }

    @Override
    public HTTPCheck id(String ID) {
        return (HTTPCheck) super.id(ID);
    }

    @Override
    public HTTPCheck status(HealthStatus status) {
        return (HTTPCheck) super.status(status);
    }

    @Override
    public HTTPCheck notes(String notes) {
        return (HTTPCheck) super.notes(notes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        HTTPCheck httpCheck = (HTTPCheck) o;
        return TLSSkipVerify == httpCheck.TLSSkipVerify &&
                Objects.equals(interval, httpCheck.interval) &&
                Objects.equals(url, httpCheck.url) &&
                method == httpCheck.method &&
                Objects.equals(headers, httpCheck.headers);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), interval, url, method, TLSSkipVerify, headers);
    }
}
