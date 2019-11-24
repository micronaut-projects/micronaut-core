package io.micronaut.docs.server.binding

import io.micronaut.core.convert.format.Format;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header

import javax.annotation.Nullable
import java.time.ZonedDateTime;

@Controller("/binding")
class BindingController {

    // tag::cookie1[]
    @Get("/cookieName")
    String cookieName(@CookieValue("myCookie") String myCookie) {
        // ...
    // end::cookie1[]
        myCookie
    // tag::cookie1[]
    }
    // end::cookie1[]

    // tag::cookie2[]
    @Get("/cookieInferred")
    String cookieInferred(@CookieValue String myCookie) {
        // ...
    // end::cookie2[]
        myCookie
    // tag::cookie2[]
    }
    // end::cookie2[]

    // tag::cookieMultiple[]
    @Get("/cookieMultiple")
    List<String> cookieMultiple(@CookieValue("myCookieA") String myCookieA, @CookieValue("myCookieB") String myCookieB) {
        // ...
        // end::cookieMultiple[]
        [myCookieA, myCookieB]
        // tag::cookieMultiple[]
    }
    // end::cookieMultiple[]

    // tag::header1[]
    @Get("/headerName")
    String headerName(@Header("Content-Type") String contentType) {
        // ...
        // end::header1[]
        contentType
        // tag::header1[]
    }
    // end::header1[]

    // tag::header2[]
    @Get("/headerInferred")
    String headerInferred(@Header String contentType) {
        // ...
        // end::header2[]
        contentType
        // tag::header2[]
    }
    // end::header2[]

    // tag::header3[]
    @Get("/headerNullable")
    String headerNullable(@Nullable @Header String contentType) {
        // ...
        // end::header3[]
        contentType
        // tag::header3[]
    }
    // end::header3[]

    // tag::format1[]
    @Get("/date")
    String date(@Header ZonedDateTime date) {
        // ...
        // end::format1[]
        date.toString()
        // tag::format1[]
    }
    // end::format1[]

    // tag::format2[]
    @Get("/dateFormat")
    String dateFormat(@Format("dd/MM/yyyy hh:mm:ss a z") @Header ZonedDateTime date) {
        // ...
        // end::format2[]
        date.toString()
        // tag::format2[]
    }
    // end::format2[]
}
