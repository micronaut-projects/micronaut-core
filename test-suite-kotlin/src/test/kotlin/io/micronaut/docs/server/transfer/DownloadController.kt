package io.micronaut.docs.server.transfer

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import reactor.core.publisher.Flux

@Controller("/download")
class DownloadController {
    // tag::class[]
    @Get("/csv")
    fun downloadCsv(): HttpResponse<Flux<String>> {
        val data = Flux.just(
            "data1,data2",
            "data3,data4"
        )
        return HttpResponse.ok(data)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data.csv\"")
            .contentType(MediaType.TEXT_PLAIN_TYPE)
    }
    // end::class[]
}
