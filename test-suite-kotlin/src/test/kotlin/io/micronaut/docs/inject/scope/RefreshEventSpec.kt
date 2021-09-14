package io.micronaut.docs.inject.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Assert.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.*
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject

class RefreshEventSpec {

    lateinit var embeddedServer: EmbeddedServer
    lateinit var client: HttpClient

    @BeforeEach
    fun setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to RefreshEventSpec::class.simpleName), Environment.TEST)
        client = HttpClient.create(embeddedServer.url)
    }

    @AfterEach
    fun teardown() {
        client.close()
        embeddedServer.close()
    }

    @Test
    fun publishingARefreshEventDestroysBeanWithRefreshableScope() {
        val firstResponse = fetchForecast()

        assertTrue(firstResponse.contains("{\"forecast\":\"Scattered Clouds"))

        val secondResponse = fetchForecast()

        assertEquals(firstResponse, secondResponse)

        val response = evictForecast()

        assertEquals(
                // tag::evictResponse[]
                "{\"msg\":\"OK\"}", response)// end::evictResponse[]

        val thirdResponse = fetchForecast()

        assertNotEquals(thirdResponse, secondResponse)
        assertTrue(thirdResponse.contains("\"forecast\":\"Scattered Clouds"))
    }

    fun fetchForecast(): String {
        return client.toBlocking().retrieve("/weather/forecast")
    }

    fun evictForecast(): String {
        return client.toBlocking().retrieve(HttpRequest.POST(
                "/weather/evict",
                emptyMap<String, String>()
        ))
    }

    //tag::weatherService[]
    @Refreshable // <1>
    open class WeatherService {
        private var forecast: String? = null

        @PostConstruct
        open fun init() {
            forecast = "Scattered Clouds " + SimpleDateFormat("dd/MMM/yy HH:mm:ss.SSS").format(Date())// <2>
        }

        open fun latestForecast(): String? {
            return forecast
        }
    }
    //end::weatherService[]

    @Requires(property = "spec.name", value = "RefreshEventSpec")
    @Controller("/weather")
    open class WeatherController(@Inject private val weatherService: WeatherService, @Inject private val applicationContext: ApplicationContext) {

        @Get(value = "/forecast")
        fun index(): MutableHttpResponse<Map<String, String?>>? {
            return HttpResponse.ok(mapOf("forecast" to weatherService.latestForecast()))
        }

        @Post("/evict")
        fun evict(): HttpResponse<Map<String, String>> {
            //tag::publishEvent[]
            applicationContext.publishEvent(RefreshEvent())
            //end::publishEvent[]
            return HttpResponse.ok(mapOf("msg" to "OK"))
        }
    }
}
