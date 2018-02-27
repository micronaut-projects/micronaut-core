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
import example.storefront.client.v1.Comment
import example.storefront.client.v1.CommentClient
import example.storefront.client.v1.PetClient
import example.storefront.client.v1.TweetClient
import example.storefront.client.v1.VendorClient
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Parameter
import org.particleframework.http.annotation.Produces
import org.particleframework.http.client.Client
import org.particleframework.http.client.RxStreamingHttpClient
import org.particleframework.http.sse.Event

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
*/
@Singleton
@Controller("/")
class StoreController {

    private final RxStreamingHttpClient httpClient
    private final VendorClient vendorClient
    private final PetClient petClient
    private final CommentClient commentClient
    private final TweetClient tweetClient

    StoreController(
            @Client(id = 'offers') RxStreamingHttpClient httpClient,
            VendorClient vendorClient,
            PetClient petClient,
            CommentClient commentClient,
            TweetClient tweetClient) {
        this.httpClient = httpClient
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
        httpClient.jsonStream(HttpRequest.GET('/v1/offers'), Offer).map({ offer ->
            Event.of(offer)
        })
    }

    @Get(uri = "/tweet/{message}")
    Single<TweetClient.Result> tweet(String message) {
        tweetClient.updateStatus(message)
    }

    @Get('/pets')
    Single<List<Pet>> pets() {
        petClient.list()
                .onErrorReturnItem(Collections.emptyList())
    }

    @Get('/pets/{slug}')
    Maybe<Pet> showPet(@Parameter('slug') String slug) {
        petClient.find(slug)
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

    @Get('/pets/{slug}/comments')
    List<Comment> petComments(String slug) {
        commentClient.list(slug)
    }
}
