from typing import Annotated

import java
from micronaut.core.convert.format import Format
from micronaut.http.annotation import Controller, CookieValue, Get, Header

ZonedDateTime = java.type("java.time.ZonedDateTime")
DateTimeFormatter = java.type("java.time.format.DateTimeFormatter")


@Controller("/binding")
class BindingController:

    # tag::cookie1[]
    @Get("/cookieName")
    def cookie_name(self, myCookie: Annotated[str, CookieValue("myCookie")]) -> str:
        # ...
    # end::cookie1[]
        return myCookie
    # tag::cookie1[]
    # end::cookie1[]

    # tag::cookie2[]
    @Get("/cookieInferred")
    def cookie_inferred(self, myCookie: Annotated[str, CookieValue]) -> str:
        # ...
    # end::cookie2[]
        return myCookie
    # tag::cookie2[]
    # end::cookie2[]

    # tag::cookieMultiple[]
    @Get("/cookieMultiple")
    def cookie_multiple(
        self,
        myCookieA: Annotated[str, CookieValue("myCookieA")],
        myCookieB: Annotated[str, CookieValue("myCookieB")],
    ) -> list[str]:
        # ...
        # end::cookieMultiple[]
        return [myCookieA, myCookieB]
        # tag::cookieMultiple[]
    # end::cookieMultiple[]

    # tag::header1[]
    @Get("/headerName")
    def header_name(self, contentType: Annotated[str, Header("Content-Type")]) -> str:
        # ...
        # end::header1[]
        return contentType
        # tag::header1[]
    # end::header1[]

    # tag::header2[]
    @Get("/headerInferred")
    def header_inferred(self, contentType: Annotated[str, Header]) -> str:
        # ...
        # end::header2[]
        return contentType
        # tag::header2[]
    # end::header2[]

    # tag::header3[]
    @Get("/headerNullable")
    def header_nullable(self, contentType: Annotated[str | None, Header]) -> str:
        # ...
        # end::header3[]
        return contentType
        # tag::header3[]
    # end::header3[]

    # tag::format1[]
    @Get("/date")
    def date(self, date: Annotated[ZonedDateTime, Header]) -> str:
        # ...
        # end::format1[]
        return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(date)
        # tag::format1[]
    # end::format1[]

    # tag::format2[]
    @Get("/dateFormat")
    def date_format(self, date: Annotated[ZonedDateTime, Header, Format("dd/MM/yyyy hh:mm:ss a z")]) -> str:
        # ...
        # end::format2[]
        return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(date)
        # tag::format2[]
    # end::format2[]
