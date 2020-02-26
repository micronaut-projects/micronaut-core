/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.binding

import io.micronaut.core.convert.format.Format
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import java.time.ZonedDateTime

@Controller("/binding")
class BindingController {

    // tag::cookie1[]
    @Get("/cookieName")
    fun cookieName(@CookieValue("myCookie") myCookie: String): String {
        // ...
        // end::cookie1[]
        return myCookie
        // tag::cookie1[]
    }
    // end::cookie1[]

    // tag::cookie2[]
    @Get("/cookieInferred")
    fun cookieInferred(@CookieValue myCookie: String): String {
        // ...
        // end::cookie2[]
        return myCookie
        // tag::cookie2[]
    }
    // end::cookie2[]

    // tag::cookieMultiple[]
    @Get("/cookieMultiple")
    fun cookieMultiple(@CookieValue("myCookieA") myCookieA: String, @CookieValue("myCookieB") myCookieB: String): List<String> {
        // ...
        // end::cookieMultiple[]
        return listOf(myCookieA, myCookieB)
        // tag::cookieMultiple[]
    }
    // end::cookieMultiple[]


    // tag::header1[]
    @Get("/headerName")
    fun headerName(@Header("Content-Type") contentType: String): String {
        // ...
        // end::header1[]
        return contentType
        // tag::header1[]
    }
    // end::header1[]

    // tag::header2[]
    @Get("/headerInferred")
    fun headerInferred(@Header contentType: String): String {
        // ...
        // end::header2[]
        return contentType
        // tag::header2[]
    }
    // end::header2[]

    // tag::header3[]
    @Get("/headerNullable")
    fun headerNullable(@Header contentType: String?): String? {
        // ...
        // end::header3[]
        return contentType
        // tag::header3[]
    }
    // end::header3[]

    // tag::format1[]
    @Get("/date")
    fun date(@Header date: ZonedDateTime): String {
        // ...
        // end::format1[]
        return date.toString()
        // tag::format1[]
    }
    // end::format1[]

    // tag::format2[]
    @Get("/dateFormat")
    fun dateFormat(@Format("dd/MM/yyyy hh:mm:ss a z") @Header date: ZonedDateTime): String {
        // ...
        // end::format2[]
        return date.toString()
        // tag::format2[]
    }
    // end::format2[]
}
