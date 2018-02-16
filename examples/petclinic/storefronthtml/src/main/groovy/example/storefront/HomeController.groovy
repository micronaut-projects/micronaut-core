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
import io.reactivex.Single
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Produces
import org.particleframework.http.cookie.Cookie
import org.particleframework.http.cookie.CookieFactory

import javax.inject.Inject
import javax.inject.Singleton
import org.particleframework.http.annotation.Cookie

/**
 * @author graemerocher
 * @since 1.0
 */
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

    @Produces(MediaType.TEXT_HTML)
    @Get('/')
    HttpResponse<Single<String>> index(@Cookie("micronautUUId") Optional<String> micronautUUIdCookie) {
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
    @Get('/vendors')
    Single<String> vendors() {
        vendorsHtmlRenderer.renderVendorsTable()
    }
}
