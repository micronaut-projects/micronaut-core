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
package io.micronaut.http.hateoas;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * A type that can be used to represent JSON errors that returns a content type of Vnd.Error.
 *
 * @author graemerocher
 * @since 1.1
 */
@Produces(MediaType.APPLICATION_VND_ERROR)
@Serdeable
public class VndError extends JsonError {

    /**
     * @param message The message
     */
    public VndError(String message) {
        super(message);
    }

    /**
     * Used by Jackson.
     */
    @Internal
    VndError() {
    }

    @Override
    public VndError path(@Nullable String path) {
        return (VndError) super.path(path);
    }

    @Override
    public VndError logref(@Nullable String logref) {
        return (VndError) super.logref(logref);
    }

    @Override
    public VndError link(@Nullable CharSequence ref, @Nullable Link link) {
        return (VndError) super.link(ref, link);
    }

    @Override
    public VndError link(@Nullable CharSequence ref, @Nullable String link) {
        return (VndError) super.link(ref, link);
    }

    @Override
    public VndError embedded(CharSequence ref, Resource resource) {
        return (VndError) super.embedded(ref, resource);
    }

    @Override
    public VndError embedded(CharSequence ref, Resource... resource) {
        return (VndError) super.embedded(ref, resource);
    }

    @Override
    public VndError embedded(CharSequence ref, List<Resource> resourceList) {
        return (VndError) super.embedded(ref, resourceList);
    }
}
