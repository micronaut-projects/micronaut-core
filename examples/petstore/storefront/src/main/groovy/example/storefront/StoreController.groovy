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

import example.api.v1.Offer
import example.api.v1.Pet
import example.api.v1.Vendor
import example.storefront.client.v1.CommentClient
import example.storefront.client.v1.PetClient
import example.storefront.client.v1.TweetClient
import example.storefront.client.v1.VendorClient
import io.micronaut.context.annotation.Parameter
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.Client
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.http.sse.Event

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
*/
@Controller("/")
class StoreController {

    private final RxStreamingHttpClient offersClient
    private final VendorClient vendorClient
    private final PetClient petClient
    private final CommentClient commentClient
    private final TweetClient tweetClient

    StoreController(
            @Client(id = 'offers') RxStreamingHttpClient offersClient,
            VendorClient vendorClient,
            PetClient petClient,
            CommentClient commentClient,
            TweetClient tweetClient) {
        this.offersClient = offersClient
        this.vendorClient = vendorClient
        this.petClient = petClient
        this.commentClient = commentClient
        this.tweetClient = tweetClient
    }

    @Produces(MediaType.TEXT_HTML)
    @Get(uri = '/')
    HttpResponse index() {
        HttpResponse.redirect(URI.create('/index.html'))
    }

    @Get(uri = "/offers", produces = MediaType.TEXT_EVENT_STREAM)
    Flowable<Event<Offer>> offers() {
        offersClient.jsonStream(HttpRequest.GET('/v1/offers'), Offer).map({ offer ->
            Event.of(offer)
        })
    }

    @Get('/pets')
    Single<List<Pet>> pets() {
        petClient.list()
                .onErrorReturnItem(Collections.emptyList())
    }

    @Get('/pets/{slug}')
    Maybe<Pet> showPet(@Parameter('slug') String slug) {
        petClient.find slug
    }

    @Get('/pets/random')
    Maybe<Pet> randomPet() {
        petClient.random()
    }


    @Get('/pets/vendor/{vendor}')
    Single<List<Pet>> petsForVendor(String vendor) {
        petClient.byVendor(vendor)
                .onErrorReturnItem(Collections.emptyList())
    }

    @Get('/vendors')
    Single<List<Vendor>> vendors() {
        vendorClient.list()
                    .onErrorReturnItem(Collections.emptyList())
    }

}
