import java
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get, ContentDisposition

ContentDispositionType = java.type("io.micronaut.http.annotation.ContentDisposition$Type")


@Requires(property="spec.name", value="contentdisposition")
@Controller("/content-disposition")
class ContentDispositionController:

    # tag::attachment[]
    @ContentDisposition(filename="report.csv")
    @Get(value="/report", produces=MediaType.TEXT_PLAIN)
    def report(self) -> str:
        return "name,amount\nwidget,42"
    # end::attachment[]

    # tag::inline[]
    @ContentDisposition(type=ContentDispositionType.INLINE)
    @Get(value="/preview", produces=MediaType.TEXT_PLAIN)
    def preview(self) -> str:
        return "This is displayed rather than downloaded"
    # end::inline[]
