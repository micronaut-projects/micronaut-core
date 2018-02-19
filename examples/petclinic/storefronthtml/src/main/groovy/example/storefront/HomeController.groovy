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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.reactivex.Single
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post
import org.particleframework.http.annotation.Produces
import org.particleframework.http.annotation.CookieValue
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

    @Produces(MediaType.TEXT_HTML)
    @Get('/pets')
    Single<String> pets() {
        petGridHtmlRenderer.renderPetGrid()
    }

    @Produces(MediaType.TEXT_HTML)
    @Get("/pets/{id}")
    Single<String> pet(Long id) {
        petHtmlRenderer.renderPet(id)
    }

    @Produces(MediaType.TEXT_HTML)
    @Post(uri= '/pet/requestInfo', consumes=MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse requestInfo(String email, Long id) {
        mailClient.send(email)
        HttpResponse.permanentRedirect(URI.create("/pets/${id}"))
    }


    @Produces(MediaType.TEXT_HTML)
    @Get('/vendors')
    Single<String> vendors() {
        vendorsHtmlRenderer.renderVendorsTable()
    }
}
