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
package example.offers;

import example.api.v1.Offer;
import reactor.core.publisher.Mono;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * @author graemerocher
 * @since 1.0
 */
public interface OffersOperations {
    /**
     * Save an offer for the given pet, vendor etc.
     *
     * @param slug pet's slug
     * @param price The price
     * @param duration The duration of the offer
     * @param description The description of the offer
     * @return The offer if it was possible to save it as a {@link Mono} or a empty {@link Mono} if no pet exists to create the offer for
     */
    Mono<Offer> save(
            @NotBlank String slug,
            @Digits(integer = 6, fraction = 2) BigDecimal price,
            @NotNull Duration duration,
            @NotBlank String description);
}
