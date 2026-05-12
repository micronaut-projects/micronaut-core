# tag::imports[]
from micronaut.runtime import Micronaut
# end::imports[]


# tag::class[]
class Application:
    @staticmethod
    def main(args: list[str]) -> None:
        Micronaut.run(Application)
# end::class[]
