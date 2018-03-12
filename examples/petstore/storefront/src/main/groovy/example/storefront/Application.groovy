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
package example.storefront

import example.api.v1.Pet
import example.api.v1.PetType
import example.api.v1.Vendor
import example.storefront.client.v1.PetClient
import example.storefront.client.v1.VendorClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.Micronaut
import io.netty.util.ResourceLeakDetector
import io.reactivex.Flowable
import io.reactivex.Single
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.server.event.ServerStartupEvent

import javax.inject.Singleton

@Singleton
@Slf4j
@CompileStatic
class Application implements ApplicationEventListener<ServerStartupEvent> {
    
    final PetClient petClient
    final VendorClient vendorClient

    Application(PetClient petClient, VendorClient vendorClient) {
        this.petClient = petClient
        this.vendorClient = vendorClient
    }

    @Override
    void onApplicationEvent(ServerStartupEvent event) {
        def names = ["Fred", "Arthur", "Joe"]
        List<Flowable<Pet>> saves = []
        for(name in names) {
            saves << vendorClient.save(name).toFlowable().flatMap({ Vendor vendor ->
                List<Single<Pet>> operations = []
                String vendorName = vendor.name
                if(vendorName == 'Fred') {
                    operations << petClient.save(new Pet(vendorName, "Harry","photo-1457914109735-ce8aba3b7a79.jpeg" ).type(PetType.DOG))
                    operations << petClient.save(new Pet(vendorName, "Ron", "photo-1442605527737-ed62b867591f.jpeg" ).type(PetType.DOG))
                    operations << petClient.save(new Pet(vendorName, "Malfoy", "photo-1489911646836-b08d6ca60ffe.jpeg" ).type(PetType.CAT))
                }
                else if(vendorName == 'Arthur') {
                    operations << petClient.save(new Pet(vendorName, "Hermione", "photo-1446231855385-1d4b0f025248.jpeg" ).type(PetType.DOG))
                    operations << petClient.save(new Pet(vendorName, "Crabbe", "photo-1512616643169-0520ad604fc2.jpeg").type(PetType.CAT))
                    operations << petClient.save(new Pet(vendorName, "Goyle", "photo-1505481354248-2ba5d3b9338e.jpeg" ).type(PetType.CAT))
                }
                return Single.merge(operations)
            })
        }
        Flowable.merge(saves).subscribe({}, { Throwable e ->
            log.error("An error occurred saving vendor data: ${e.message}", e)
        })
    }

    static void main(String...args) {
        Micronaut.run(Application, args)
    }
}
