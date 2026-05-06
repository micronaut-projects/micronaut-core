# tag::imports[]
from micronaut.runtime import Micronaut
# end::imports[]


# tag::class[]
class Application:
    @staticmethod
    def main(args: list[str]) -> None:
        Micronaut.build(args) \
            .mainClass(Application) \
            .environmentPropertySource(False) \
            .environmentVariableIncludes("THIS_ENV_ONLY") \
            .environmentVariableExcludes("EXCLUDED_ENV") \
            .start()
# end::class[]
