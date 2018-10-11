package io.micronaut.http.client.httpclientexceptionbody

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@CompileStatic
@Requires(property = 'spec.name', value = 'BindHttpClientExceptionBodySpec')
@Controller("/books")
class BooksController {

    @Get
    HttpResponse index() {
        HttpResponse.status(HttpStatus.UNAUTHORIZED).body([
                status: 401,
                error: 'Unauthorized',
                message: 'No message available',
                path: '/api/announcements'])
    }
}
