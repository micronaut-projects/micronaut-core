package io.micronaut.docs.server.binding;

import com.sun.tools.javac.util.List;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;

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
    public List<String> cookieName(@CookieValue("myCookieA") String myCookieA, @CookieValue("myCookieB") String myCookieB) {
        // ...
        // end::cookieMultiple[]
        return List.of(myCookieA, myCookieB);
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
