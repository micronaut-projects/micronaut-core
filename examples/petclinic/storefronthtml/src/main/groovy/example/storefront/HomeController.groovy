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

import example.api.v1.Email
import example.api.v1.Offer
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.reactivex.Flowable
import io.reactivex.Single
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post
import org.particleframework.http.annotation.Produces
import org.particleframework.http.annotation.CookieValue
import org.particleframework.http.client.Client
import org.particleframework.http.client.RxStreamingHttpClient
import org.particleframework.http.sse.Event

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author sdelamo
 * @since 1.0
 */
@Slf4j
@Singleton
@Controller("/")
@CompileStatic
class HomeController {

    private final RxStreamingHttpClient offersHttpClient

    HomeController(@Client(id = 'offers') RxStreamingHttpClient offersHttpClient) {
        this.offersHttpClient = offersHttpClient
    }

    @Inject
    PetGridHtmlRenderer petGridHtmlRenderer

    @Inject
    VendorsHtmlRenderer vendorsHtmlRenderer

    @Inject
    HomeHtmlRenderer homeHtmlRenderer

    @Inject
    PetStoreCookieGenerator petStoreCookieGenerator

    @Inject
    PetHtmlRenderer petHtmlRenderer

    @Inject
    MailClient mailClient

    @Produces(MediaType.TEXT_HTML)
    @Get('/')
    HttpResponse<Single<String>> index(@CookieValue("micronautUUId") Optional<String> micronautUUIdCookie) {
        if ( micronautUUIdCookie.isPresent() ) {
            return HttpResponse.ok(homeHtmlRenderer.render())
        }
        HttpResponse.ok(homeHtmlRenderer.render()).cookie(petStoreCookieGenerator.generate())
    }

    @CompileDynamic
    @Get(uri = "/offers", produces = MediaType.TEXT_EVENT_STREAM)
    Flowable<Event<Offer>> offers() {
        offersHttpClient.jsonStream(HttpRequest.GET('/v1/offers'), Offer).map({ offer ->
            Event.of(offer)
        })
    }

    @Produces(MediaType.TEXT_HTML)
    @Get('/pets')
    Single<String> pets() {
        petGridHtmlRenderer.renderPetGrid()
    }

    @Produces(MediaType.TEXT_HTML)
    @Get("/pets/{slug}")
    Single<String> pet(String slug) {
        petHtmlRenderer.renderPet(slug)
    }

    @Produces(MediaType.TEXT_HTML)
    @Post(uri= '/pet/requestInfo', consumes=MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse requestInfo(String email, String slug) {
        Email emailDTO = new Email()
        emailDTO.setRecipient(email)
        mailClient.send(emailDTO)
        HttpResponse.redirect(URI.create("/pets/${slug}"))
    }

    @Produces(MediaType.TEXT_HTML)
    @Get('/vendors')
    Single<String> vendors() {
        vendorsHtmlRenderer.renderVendorsTable()
    }
}
