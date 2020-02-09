/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.http.annotation.*

import javax.inject.Singleton
import java.math.RoundingMode


@Issue("https://github.com/micronaut-projects/micronaut-core/issues/1089")
class BigDecimalSerializationSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(
            RxHttpClient,
            embeddedServer.getURL()
    )

    void "test that big decimal precision is retained during JSON ser-de"() {
        given:
        BigDecimal bigDecimal = BigDecimal.valueOf(48.669969)

        def pojo = new TestPojo(bigDecimal)
        pojo.f = 1.12345f
        pojo.d = 1.123456789d
        HttpRequest<TestPojo> request = HttpRequest.POST("/big-decimal/test", pojo)
                .contentType(MediaType.APPLICATION_JSON_TYPE)

        when:
        TestPojo response = client.toBlocking().retrieve(request, TestPojo.class)

        then:
        response

        when:
        BigDecimal actual = response.getDecimal()
        System.out.println(bigDecimal)
        System.out.println(actual)

        then:
        response.f == pojo.f
        response.d == pojo.d
        bigDecimal == actual
    }


    @Controller("/big-decimal/test")
    static class TestController {

        @Post
        TestPojo get(@Body TestPojo test) {
            return test
        }
    }

    static class TestPojo {

        BigDecimal decimal
        float f
        double d

        TestPojo(BigDecimal decimal) {
            this.decimal = decimal
        }
        TestPojo() {
        }
    }

    @Singleton
    static class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

        @Override
        BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getDecimalValue().setScale(6, RoundingMode.UP)
        }
    }

    @Singleton
    static class BigDecimalSerializer extends JsonSerializer<BigDecimal> {

        @Override
        void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNumber(value.setScale(6, RoundingMode.UP))
        }
    }
}
