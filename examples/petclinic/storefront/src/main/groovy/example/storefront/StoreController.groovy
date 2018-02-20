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
import io.reactivex.Flowable
import org.particleframework.http.HttpRequest
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
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

    StoreController(@Client(id = 'offers') RxStreamingHttpClient httpClient) {
        this.httpClient = httpClient
    }

    @Get(uri = "/offers", produces = MediaType.TEXT_EVENT_STREAM)
    Flowable<Event<Offer>> offers() {
        httpClient.jsonStream(HttpRequest.GET('/v1/offers'), Offer).map({ offer ->
            Event.of(offer)
        })
    }

}
