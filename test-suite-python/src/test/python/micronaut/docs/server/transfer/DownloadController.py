import java

from micronaut.http import HttpHeaders, HttpResponse, MediaType
from micronaut.http.annotation import Controller, Get

Flux = java.type("reactor.core.publisher.Flux")


@Controller("/download")
class DownloadController:
    # tag::class[]
    @Get("/csv")
    def downloadCsv(self) -> HttpResponse:
        data = Flux.just(
            "data1,data2",
            "data3,data4",
        )
        return HttpResponse.ok(data).header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data.csv\""
        ).contentType(MediaType.TEXT_PLAIN_TYPE)
    # end::class[]
