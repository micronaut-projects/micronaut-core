package io.micronaut.docs.httpclientexceptionbody

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import java.util.HashMap

@Requires(property = "spec.name", value = "BindHttpClientExceptionBodySpec")
//tag::clazz[]
@Controller("/books")
class BooksController {

    @Get("/{isbn}")
    fun find(isbn: String): HttpResponse<*> {
        if (isbn == "1680502395") {
            val m = HashMap<String, Any>()
            m["status"] = 401
            m["error"] = "Unauthorized"
            m["message"] = "No message available"
            m["path"] = "/books/$isbn"
            return HttpResponse.status<Any>(HttpStatus.UNAUTHORIZED).body(m)

        }
        return HttpResponse.ok(Book("1491950358", "Building Microservices"))
    }
}
//end::clazz[]
