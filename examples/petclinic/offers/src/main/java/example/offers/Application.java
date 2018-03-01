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
import example.offers.client.v1.PetClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.runtime.ParticleApplication;
import org.particleframework.runtime.server.event.ServerStartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class Application implements ApplicationEventListener<ServerStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private final PetClient petClient;
    private final OffersRepository offersRepository;
    private final StatefulRedisConnection<String, String> redisConnection;

    public Application(PetClient petClient, OffersRepository offersRepository, StatefulRedisConnection<String, String> redisConnection) {
        this.petClient = petClient;
        this.offersRepository = offersRepository;
        this.redisConnection = redisConnection;
    }

    public static void main(String... args) {
        ParticleApplication.run(Application.class);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        try {
            redisConnection.sync().flushall();
        } catch (Exception e) {
            LOG.error("Error flushing Redis data: " +e.getMessage(), e);
        }
        if(LOG.isInfoEnabled()) {
            LOG.info("Creating Initial Offers for Pets: {}", petClient.list().blockingGet());

        }
        petClient.find("harry")
                .doOnError(throwable -> {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("No pet found: " + throwable.getMessage(), throwable);
                    }
                })
                .onErrorComplete()
                .subscribe(pet -> {
                            Mono<Offer> savedOffer = offersRepository.save(
                                    pet.getSlug(),
                                    new BigDecimal("49.99"),
                                    Duration.of(2, ChronoUnit.HOURS),
                                    "Cute dog!");
                            savedOffer.subscribe((offer) -> {
                            }, throwable -> {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Error occurred saving offer: " + throwable.getMessage(), throwable);
                                }
                            });
                        }
                );

        petClient.find("malfoy")
                .doOnError(throwable -> {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("No pet found: " + throwable.getMessage(), throwable);
                    }
                })
                .onErrorComplete()
                .subscribe(pet -> {
                            Mono<Offer> savedOffer = offersRepository.save(
                                    pet.getSlug(),
                                    new BigDecimal("29.99"),
                                    Duration.of(2, ChronoUnit.HOURS),
                                    "Special Cat! Offer ends soon!");
                            savedOffer.subscribe((offer) -> {
                            }, throwable -> {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Error occurred saving offer: " + throwable.getMessage(), throwable);
                                }
                            });
                        }
                );

        petClient.find("goyle")
                .doOnError(throwable -> {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("No pet found: " + throwable.getMessage(), throwable);
                    }
                })
                .onErrorComplete()
                .subscribe(pet -> {
                            Mono<Offer> savedOffer = offersRepository.save(
                                    pet.getSlug(),
                                    new BigDecimal("39.99"),
                                    Duration.of(1, ChronoUnit.DAYS),
                                    "Carefree Cat! Low Maintenance! Looking for a Home!");
                            savedOffer.subscribe((offer) -> {
                            }, throwable -> {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Error occurred saving offer: " + throwable.getMessage(), throwable);
                                }
                            });
                        }
                );
    }
}
