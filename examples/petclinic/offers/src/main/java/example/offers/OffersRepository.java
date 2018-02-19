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
import example.offers.client.v1.Pet;
import example.offers.client.v1.PetClient;
import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.validation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Validated
public class OffersRepository implements OffersOperations {

    private final PetClient petClient;
    private final StatefulRedisConnection<String, String> redisConnection;

    public OffersRepository(PetClient petClient, StatefulRedisConnection<String, String> redisConnection) {
        this.petClient = petClient;
        this.redisConnection = redisConnection;
    }

    /**
     * @return Obtain a random offer or return {@link Mono#empty()} if there is none
     */
    public Mono<Offer> random() {
        RedisReactiveCommands<String, String> reactiveRedis = redisConnection.reactive();
        return reactiveRedis.randomkey().flatMap(key -> {
            Flux<KeyValue<String, String>> values = reactiveRedis.hmget(key, "price", "description");

            Map<String, String> map = new HashMap<>(3);
            return values.reduce(map, (all, keyValue) -> {
                all.put(keyValue.getKey(), keyValue.getValue());
                return all;
            })
            .map(ConvertibleValues::of)
            .map(entries -> {
                String[] vendorAndName = key.split(":");
                String description = entries.get("description", String.class).orElseThrow(() -> new IllegalStateException("No description"));
                BigDecimal price = entries.get("price", BigDecimal.class).orElseThrow(() -> new IllegalStateException("No description"));

                return new Offer(new Pet(vendorAndName[0], vendorAndName[1]), description, price);
            });
        });
    }

    /**
     * Save an offer for the given pet, vendor etc.
     *
     * @param vendor The vendor
     * @param pet The pet
     * @param price The price
     * @param duration The duration of the offer
     * @param description The description of the offer
     * @return The offer if it was possible to save it as a {@link Mono} or a empty {@link Mono} if no pet exists to create the offer for
     */
    @Override
    public Mono<Offer> save(
            String vendor,
            String pet,
            BigDecimal price,
            Duration duration,
            String description) {

        return Mono.from(petClient.find(
                vendor, pet
        ).toFlowable())
         .flatMap(petInstance -> {
             ZonedDateTime expiryDate = ZonedDateTime.now().plus(duration);
             Offer offer = new Offer(
                     petInstance,
                     description,
                     price
             );

             Map<String, String> data = new LinkedHashMap<>(4);
             data.put("currency", offer.getCurrency().getCurrencyCode());
             data.put("price", offer.getPrice().toString());
             data.put("description" ,offer.getDescription());
             String key = petInstance.key();
             RedisReactiveCommands<String, String> redisApi = redisConnection.reactive();
             return redisApi.hmset(key,data)
                            .flatMap(success-> redisApi.expireat(key, expiryDate.toEpochSecond() ))
                            .map(ok -> offer) ;
         });

    }
}
