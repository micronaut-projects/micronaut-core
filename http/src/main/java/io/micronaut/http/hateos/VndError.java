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
package io.micronaut.http.hateos;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * See https://github.com/blongden/vnd.error
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces(MediaType.APPLICATION_VND_ERROR)
public class VndError extends AbstractResource<VndError> {

    private String message;
    private String logref;
    private String path;

    public VndError(String message) {
        this.message = message;
    }

    VndError() {
    }

    void setMessage(String message) {
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
     * Sets the path
     *
     * @param path The path
     * @return This error object
     */
    public VndError path(@Nullable String path) {
        this.path = path;
        return this;
    }

    /**
     * Sets the logref
     *
     * @param logref The logref
     * @return This error object
     */
    public VndError logref(@Nullable String logref) {
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
