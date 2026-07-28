
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean
from .Engine import Engine

# tag::class[]
@Singleton # <2>
@Bean(typed = Engine)
class V8Engine(Engine):
    cylinders: int = 8

    def start(self) -> str:
        return "Starting V8"

    def get_cylinders(self) -> int:
        return self.cylinders
# end::class[]
