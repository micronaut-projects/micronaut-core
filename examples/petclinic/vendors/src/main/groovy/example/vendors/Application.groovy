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
package example.vendors

import example.api.v1.Pet
import example.api.v1.PetType
import example.vendors.client.v1.PetClient
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.reactivex.Single
import org.particleframework.context.event.ApplicationEventListener
import org.particleframework.runtime.ParticleApplication
import org.particleframework.runtime.server.event.ServerStartupEvent

import javax.inject.Singleton

@CompileStatic
@Singleton
@Slf4j
class Application implements ApplicationEventListener<ServerStartupEvent>{
    final VendorService vendorService
    final PetClient petClient

    Application(VendorService vendorService, PetClient petClient) {
        this.vendorService = vendorService
        this.petClient = petClient
    }

    static void main(String...args) {
        ParticleApplication.run(Application, args)
    }

    @Override
    @Transactional
    void onApplicationEvent(ServerStartupEvent event) {
        def names = ["Fred", "Arthur", "Joe"]
        for(name in names) {
            vendorService.save(name)
        }
        List<Single<Pet>> saves = []
        for(vendor in names) {
            if(vendor == 'Fred') {
                saves << petClient.save(new Pet(vendor, "Harry","photo-1457914109735-ce8aba3b7a79.jpeg" ).type(PetType.DOG))
                saves << petClient.save(new Pet(vendor, "Ron", "photo-1442605527737-ed62b867591f.jpeg" ).type(PetType.DOG))
                saves << petClient.save(new Pet(vendor, "Malfoy", "photo-1489911646836-b08d6ca60ffe.jpeg" ).type(PetType.CAT))
            }
            else if(vendor == 'Arthur') {
                saves << petClient.save(new Pet(vendor, "Hermione", "photo-1446231855385-1d4b0f025248.jpeg" ).type(PetType.DOG))
                saves << petClient.save(new Pet(vendor, "Crabbe", "photo-1512616643169-0520ad604fc2.jpeg").type(PetType.CAT))
                saves << petClient.save(new Pet(vendor, "Goyle", "photo-1505481354248-2ba5d3b9338e.jpeg" ).type(PetType.CAT))
            }
        }
        Single.merge(saves).subscribe({}, { Throwable e ->
            log.error("An error occurred saving vendor data: ${e.message}", e)
        })
    }
}
