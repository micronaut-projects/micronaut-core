package org.particleframework.http.server.netty.status

import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Consumes
import org.particleframework.web.router.annotation.Error
import org.particleframework.web.router.annotation.Get
import org.particleframework.web.router.annotation.Post

/**
 * Created by graemerocher on 13/09/2017.
 */
class CustomStaticMappingSpec extends AbstractParticleSpec {

    void "test that a bad request response can be redirected by the router"() {
        when:
        def request = new Request.Builder()
                .url("$server/test/bad")
                .get()

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        applicationContext.containsBean(TestController)
        response.code() == HttpStatus.BAD_REQUEST.code
        response.message() == "You sent me bad stuff"

    }

    void "test that a bad request response for invalid request data can be redirected by the router"() {
        when:
        RequestBody formBody = new FormBody.Builder()
                .add("name", "Fred")
                .build()
        def request = new Request.Builder()
                .url("$server/test/simple")
                .post(formBody)

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        applicationContext.containsBean(TestController)
        response.code() == HttpStatus.BAD_REQUEST.code
        response.message() == "You sent me bad stuff"

    }

    @Controller
    @Requires(property = 'spec.name', value = 'CustomStaticMappingSpec')
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED )
    static class TestController {
        @Get
        HttpResponse bad() {
            HttpResponse.badRequest()
        }

        @Post
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Error(HttpStatus.BAD_REQUEST)
        HttpResponse badHandler() {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff")
        }
    }
}
