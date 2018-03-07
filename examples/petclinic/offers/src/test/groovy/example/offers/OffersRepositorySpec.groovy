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
import example.api.v1.Pet
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author graemerocher
 * @since 1.0
 */
class OffersRepositorySpec extends Specification {

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run(
            "consul.client.enabled":false,
            "redis.uri":"redis://localhost:${SocketUtils.findAvailableTcpPort()}"
    )

    void "test attempt to save offer for pet that doesn't exist"() {
        given:
        OffersRepository offersRepository = applicationContext.getBean(OffersRepository)

        when:
        Offer offer = offersRepository.save(
                "petslug",
                1.1,
                Duration.of(1, ChronoUnit.HOURS),
                "my offer"
        ).block()

        then:
        offer == null
    }

    void "test save a new offer for a pet that exists"() {
        given:
        TestPetClientFallback fallback = applicationContext.getBean(TestPetClientFallback)
        def pet = new Pet("Fred", "Harry","photo-1457914109735-ce8aba3b7a79.jpeg")
        fallback.addPet(pet)
        OffersRepository offersRepository = applicationContext.getBean(OffersRepository)

        when:"A valid offer is saved"
        Offer offer = offersRepository.save(
                pet.slug,
                100.0,
                Duration.of(1, ChronoUnit.HOURS),
                "my offer"
        ).block()

        then:"The offer is returned"
        offer != null
        offer.pet.name == pet.name
        offer.description == "my offer"
        offer.price == 100.0

        when:"A random offer is retrieved"
        offer = offersRepository.random().block()

        then:"The offer is returned since it is the only one"
        //offer.pet.name == pet.name
        offer.description == "my offer"
        offer.price == 100.0
    }
}
