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
package io.micronaut.http.uri;

import java.net.URI;
import java.util.Optional;

/**
 * <p>A URI matcher is capable of matching a URI and producing a {@link UriMatchInfo}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface UriMatcher {

    /**
     * Match the given {@link URI} object.
     *
     * @param uri The URI
     * @return True if it matches
     */
    default Optional<? extends UriMatchInfo> match(URI uri) {
        return match(uri.toString());
    }

    /**
     * Match the given URI string.
     *
     * @param uri The uRI
     * @return True if it matches
     */
    Optional<? extends UriMatchInfo> match(String uri);
}
