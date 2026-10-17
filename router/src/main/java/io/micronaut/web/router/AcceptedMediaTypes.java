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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;

import java.util.Collection;

/**
 * The checks of the accepted media types that take a quality of {@code 0} into account: an
 * accepted media range with {@code q=0} means "not acceptable", see RFC 9110, section 12.4.2.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AcceptedMediaTypes {

    private static final String WILDCARD = "*";

    private AcceptedMediaTypes() {
    }

    /**
     * @param type An accepted media type
     * @return Whether the type is accepted with the quality {@code 0}, meaning "not acceptable"
     */
    public static boolean isRejected(MediaType type) {
        return type.getQualityAsNumber().signum() == 0;
    }

    /**
     * @param accepted The accepted media types
     * @return Whether one of the accepted media types has the quality {@code 0}
     */
    public static boolean hasRejected(@Nullable Collection<MediaType> accepted) {
        if (accepted == null) {
            return false;
        }
        for (MediaType type : accepted) {
            if (isRejected(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a produced media type is acceptable: the most specific accepted media range that
     * covers it decides, a range of the quality {@code 0} rejecting it. A produced media range is
     * acceptable when it covers an accepted type with a quality above {@code 0}.
     *
     * @param produced The produced media type
     * @param accepted The accepted media types
     * @return Whether the produced type is acceptable, {@code true} without accepted types
     */
    public static boolean isAcceptable(MediaType produced, @Nullable Collection<MediaType> accepted) {
        if (accepted == null || accepted.isEmpty()) {
            return true;
        }
        MediaType best = null;
        int bestSpecificity = -1;
        for (MediaType range : accepted) {
            if (range.matches(produced)) {
                int specificity = specificity(range);
                // with the same specificity a rejection wins
                if (specificity > bestSpecificity || specificity == bestSpecificity && isRejected(range)) {
                    best = range;
                    bestSpecificity = specificity;
                }
            }
        }
        if (best != null) {
            return !isRejected(best);
        }
        for (MediaType type : accepted) {
            if (!isRejected(type) && produced.matches(type)) {
                return true;
            }
        }
        return false;
    }

    private static int specificity(MediaType range) {
        if (WILDCARD.equals(range.getType())) {
            return 0;
        }
        return WILDCARD.equals(range.getSubtype()) ? 1 : 2;
    }
}
