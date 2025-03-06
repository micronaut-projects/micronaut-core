package io.micronaut.docs.inject.scope

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

class RefreshEventSpec: AnnotationSpec() {

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

        firstResponse.shouldContain("{\"forecast\":\"Scattered Clouds")

        val secondResponse = fetchForecast()

        firstResponse shouldBe secondResponse

        val response = evictForecast()

        // tag::evictResponse[]
        response shouldBe "{\"msg\":\"OK\"}"// end::evictResponse[]

        val thirdResponse = fetchForecast()

        thirdResponse shouldNotBe secondResponse
        thirdResponse.shouldContain("\"forecast\":\"Scattered Clouds")
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
