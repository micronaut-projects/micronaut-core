from micronaut.runtime.context.scope import Refreshable
from jakarta.annotation import PostConstruct
from datetime import datetime
from micronaut.context.annotation import Requires, Property
from micronaut.http.annotation import Controller, Get, Post
from micronaut.context import ApplicationContext
from micronaut.runtime.context.scope.refresh import RefreshEvent
from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.http.client import HttpClient
from typing import Annotated
import java

# tag::weatherService[]
@Refreshable
class WeatherService:
    def __init__(self):
        self.forecast = None

    @PostConstruct
    def init(self):
        now = datetime.now()
        self.forecast = f"Scattered Clouds {now.strftime('%d/%m/%Y %H:%M:%S.')}" + f"{now.microsecond // 1000:03d}"

    def latest_forecast(self) -> str:
        return self.forecast
# end::weatherService[]

@Requires(property = "spec.name", value = "RefreshEventSpec")
@Requires(property = "spec.lang", value = "python")
@Controller("/weather")
class WeatherController:
    def __init__(self, weather_service: WeatherService, context : ApplicationContext):
        self.weather_service = weather_service
        self.context = context

    @Get("/forecast")
    def forecast(self) -> dict[str, str]:
        return {
            "forecast" : self.weather_service.latest_forecast()
        }

    @Post("/evict")
    def evict(self) -> dict[str, str]:
        self.context.publishEvent()
        return {
            "msg" : "Ok"
        }

