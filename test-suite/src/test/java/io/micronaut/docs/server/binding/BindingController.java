/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.binding;

import io.micronaut.core.convert.format.Format;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

@Controller("/binding")
public class BindingController {

    // tag::cookie1[]
    @Get("/cookieName")
    public String cookieName(@CookieValue("myCookie") String myCookie) {
        // ...
    // end::cookie1[]
        return myCookie;
    // tag::cookie1[]
    }
    // end::cookie1[]

    // tag::cookie2[]
    @Get("/cookieInferred")
    public String cookieInferred(@CookieValue String myCookie) {
        // ...
    // end::cookie2[]
        return myCookie;
    // tag::cookie2[]
    }
    // end::cookie2[]

    // tag::cookieMultiple[]
    @Get("/cookieMultiple")
    public List<String> cookieMultiple(@CookieValue("myCookieA") String myCookieA, @CookieValue("myCookieB") String myCookieB) {
        // ...
        // end::cookieMultiple[]
        return Arrays.asList(myCookieA, myCookieB);
        // tag::cookieMultiple[]
    }
    // end::cookieMultiple[]

    // tag::header1[]
    @Get("/headerName")
    public String headerName(@Header("Content-Type") String contentType) {
        // ...
        // end::header1[]
        return contentType;
        // tag::header1[]
    }
    // end::header1[]

    // tag::header2[]
    @Get("/headerInferred")
    public String headerInferred(@Header String contentType) {
        // ...
        // end::header2[]
        return contentType;
        // tag::header2[]
    }
    // end::header2[]

    // tag::header3[]
    @Get("/headerNullable")
    public String headerNullable(@Nullable @Header String contentType) {
        // ...
        // end::header3[]
        return contentType;
        // tag::header3[]
    }
    // end::header3[]

    // tag::format1[]
    @Get("/date")
    public String date(@Header ZonedDateTime date) {
        // ...
        // end::format1[]
        return date.toString();
        // tag::format1[]
    }
    // end::format1[]

    // tag::format2[]
    @Get("/dateFormat")
    public String dateFormat(@Format("dd/MM/yyyy hh:mm:ss a z") @Header ZonedDateTime date) {
        // ...
        // end::format2[]
        return date.toString();
        // tag::format2[]
    }
    // end::format2[]
}
