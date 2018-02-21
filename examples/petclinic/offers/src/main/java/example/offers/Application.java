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

    public Application(PetClient petClient, OffersRepository offersRepository) {
        this.petClient = petClient;
        this.offersRepository = offersRepository;
    }

    public static void main(String... args) {
        ParticleApplication.run(Application.class);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
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
                                    "Cut dog!");
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
                                    "Cut Cat");
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
