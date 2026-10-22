/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.UriRouteMatch;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The default {@link RouteMatchSelector.Selection}.
 *
 * @param match             One of the matches given to the selector
 * @param responseMediaType The negotiated media type of the response, or {@code null}
 * @param rejectionStatus   The status that rejects the request, {@code 406} or {@code 415}, or {@code null}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultSelection(UriRouteMatch<?, ?> match, @Nullable MediaType responseMediaType,
                        @Nullable HttpStatus rejectionStatus) implements RouteMatchSelector.Selection {

    /**
     * @param match             One of the matches given to the selector
     * @param responseMediaType The negotiated media type of the response, or {@code null}
     * @param rejectionStatus   The status that rejects the request, {@code 406} or {@code 415}, or {@code null}
     */
    DefaultSelection {
        Objects.requireNonNull(match, "match");
    }
}
