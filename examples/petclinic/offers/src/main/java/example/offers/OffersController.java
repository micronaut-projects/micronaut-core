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
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Controller;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.annotation.Post;
import org.particleframework.validation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller("/${offers.api.version}/offers")
@Singleton
@Validated
public class OffersController implements OffersOperations {

    private final OffersRepository offersRepository;

    public OffersController(OffersRepository offersRepository) {
        this.offersRepository = offersRepository;
    }

    /**
     * A non-blocking infinite JSON stream of offers that change every 10 seconds
     * @return A {@link Flux} stream of JSON objects
     */
    @Get(uri = "/", produces = MediaType.APPLICATION_JSON_STREAM)
    public Flux<Offer> current() {
        return offersRepository
                    .random()
                    .repeat()
                    .delayElements(Duration.ofSeconds(10));
    }

    /**
     * Consumes JSON and saves a new offer
     *
     * @param pet The pet the offer applies to
     * @param vendor The vendor the offer applies to
     * @param price The price of the offer
     * @param duration The duration of the offer
     * @param description The description of the offer
     * @return The offer or 404 if no pet exists for the offer
     */
    @Post("/")
    @Override
    public Mono<Offer> save(
            String vendor,
            String pet,
            BigDecimal price,
            Duration duration,
            String description) {
        return offersRepository.save(
                vendor, pet, price, duration, description
        );
    }
}
