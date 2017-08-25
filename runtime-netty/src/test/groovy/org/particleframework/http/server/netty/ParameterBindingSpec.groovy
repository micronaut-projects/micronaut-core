package org.particleframework.http.server.netty

import okhttp3.Request
import org.particleframework.http.HttpParameters
import org.particleframework.http.HttpStatus
import org.particleframework.http.binding.annotation.Parameter
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Get
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/08/2017.
 */
class ParameterBindingSpec extends AbstractParticleSpec {


    @Unroll
    void "test bind HTTP parameters for URI #uri"() {
        given:
        def request = new Request.Builder()
                .url("$server$uri")

        def response = client.newCall(
                request.build()
        ).execute()

        def status = response.code()
        def body = null
        if (status == HttpStatus.OK.code) {
            body = response.body().string()
        }
        expect:
        body == result
        HttpStatus.valueOf(status) == httpStatus



        where:
        uri                           | result                | httpStatus
        '/parameter/index?max=20'     | "Parameter Value: 20" | HttpStatus.OK
        '/parameter/simple?max=20'    | "Parameter Value: 20" | HttpStatus.OK
        '/parameter/simple'           | null                  | HttpStatus.NOT_FOUND
        '/parameter/named'            | null                  | HttpStatus.NOT_FOUND
        '/parameter/named?maximum=20' | "Parameter Value: 20" | HttpStatus.OK
        '/parameter/optional'         | "Parameter Value: 10" | HttpStatus.OK
        '/parameter/optional?max=20'  | "Parameter Value: 20" | HttpStatus.OK
        '/parameter/all'              | "Parameter Value: 10" | HttpStatus.OK
        '/parameter/all?max=20'       | "Parameter Value: 20" | HttpStatus.OK
    }

    @Controller
    static class ParameterController {
        @Get
        String index(Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String simple(@Parameter Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String named(@Parameter('maximum') Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String optional(@Parameter Optional<Integer> max) {
            "Parameter Value: ${max.orElse(10)}"
        }


        @Get
        String all(HttpParameters parameters) {
            "Parameter Value: ${parameters.get('max', Integer, 10)}"
        }
    }
}
