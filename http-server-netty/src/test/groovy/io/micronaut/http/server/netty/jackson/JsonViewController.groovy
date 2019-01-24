package io.micronaut.http.server.netty.jackson

import com.fasterxml.jackson.annotation.JsonView
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/jsonview")
class JsonViewController {

    static TestModel TEST_MODEL = new TestModel(firstName: "Bob", lastName: "Jones", birthdate: "08/01/1980", password: "secret")

    @Get("/none")
    HttpResponse<TestModel> none() {
        return HttpResponse.ok(TEST_MODEL)
    }

    @JsonView(Views.Public)
    @Get("/public")
    HttpResponse<TestModel> publicView() {
        return HttpResponse.ok(TEST_MODEL)
    }

    @JsonView(Views.Internal)
    @Get("/internal")
    HttpResponse<TestModel> internalView() {
        return HttpResponse.ok(TEST_MODEL)
    }

    @JsonView(Views.Admin)
    @Get("/admin")
    HttpResponse<TestModel> adminView() {
        return HttpResponse.ok(TEST_MODEL)
    }

}
