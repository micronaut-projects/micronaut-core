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
package example.offers

import example.api.v1.Offer
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.Client
import io.micronaut.validation.Validated
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Duration

/**
 * @author graemerocher
 * @since 1.0
 */
@Client('/${offers.api.version}/offers')
@Validated
interface TestOffersClient extends OffersOperations {
    
    @Get(uri = '/', consumes = MediaType.APPLICATION_JSON_STREAM)
    Flux<Offer> current()

    @Override
    @Post("/")
    Mono<Offer> save(
            String slug,
            BigDecimal price,
            Duration duration,
            String description)

}