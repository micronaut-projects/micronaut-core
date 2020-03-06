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
package io.micronaut.http.hateoas;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A class that can be used to represent JSON errors that complies to Vnd.Error without the content type requirements.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@Produces(MediaType.APPLICATION_JSON)
public class JsonError extends AbstractResource<JsonError> {

    /**
     * The argument type.
     *
     * @since 1.3.3
     */
    public static final Argument<JsonError> TYPE = Argument.of(JsonError.class);

    private String message;
    private String logref;
    private String path;

    /**
     * @param message The message
     */
    public JsonError(String message) {
        this.message = message;
    }

    /**
     * Used by Jackson.
     */
    @Internal
    JsonError() {
    }

    /**
     * @param message The message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return The logref
     */
    public Optional<String> getLogref() {
        return logref == null ? Optional.empty() : Optional.of(logref);
    }

    /**
     * @return The path
     */
    public Optional<String> getPath() {
        return path == null ? Optional.empty() : Optional.of(path);
    }

    /**
     * Sets the path.
     *
     * @param path The path
     * @return This error object
     */
    public JsonError path(@Nullable String path) {
        this.path = path;
        return this;
    }

    /**
     * Sets the logref.
     *
     * @param logref The logref
     * @return This error object
     */
    public JsonError logref(@Nullable String logref) {
        this.logref = logref;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (logref != null) {
            builder.append('[').append(logref).append("] ");
        }
        if (path != null) {
            builder.append(' ').append(path).append(" - ");
        }
        builder.append(message);
        return builder.toString();
    }
}
