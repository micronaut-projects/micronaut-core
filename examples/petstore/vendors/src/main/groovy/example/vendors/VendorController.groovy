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
import example.vendors.client.v1.PetClient
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated

import javax.inject.Singleton
import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller('/${vendors.api.version}/vendors')
@Validated
class VendorController {

    final VendorService vendorService
    final PetClient petClient

    VendorController(VendorService vendorService, PetClient petClient) {
        this.vendorService = vendorService
        this.petClient = petClient
    }

    @Get('/')
    Single<List<Vendor>> list() {
        return Single.fromCallable({-> vendorService.list() })
              .subscribeOn(Schedulers.io())
              .toFlowable()
              .flatMap({ List<Vendor> list ->
            Flowable.fromIterable(list)
        })
        .flatMap({ Vendor v ->
            petClient.byVendor(v.name).map({ List<Pet> pets ->
                return v.pets(pets)
            }).toFlowable()
        })
        .toList()

    }

    @Get('/names')
    List<String> names() {
        vendorService.listVendorName()
    }

    @Post('/')
    Vendor save(@NotBlank String name) {
        vendorService.findOrCreate(name)
    }
}
