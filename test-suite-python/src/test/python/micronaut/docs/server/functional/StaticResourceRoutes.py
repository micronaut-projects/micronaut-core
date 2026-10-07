from micronaut.context.annotation import Requires
# tag::imports[]
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.http import MediaType
from micronaut.http.server.routes import StaticResources
from micronaut.scheduling import TaskExecutors
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes
# end::imports[]


@Requires(property="spec.name", value="StaticResourceRoutesTest")
# tag::clazz[]
@Singleton
class StaticResourceRoutes(HttpRoutes):

    def __init__(self, site_directory: Annotated[str, Value("${site.directory}")]):
        self.site_directory = site_directory

    def routes(self, routes: HttpRouteBuilder) -> None:
        # tag::resources[]
        (routes.GET("/assets")  # <1>
            .after(lambda request, response: response.header("X-Assets", "true")).and_()  # <2>
            .resources(StaticResources.classpath("functional-static/assets")  # <3>
                       .cacheControl("public, max-age=31536000, immutable")))  # <4>

        routes.GET("/site").resources(StaticResources.fileSystem(self.site_directory))  # <5>
        # end::resources[]

        # tag::group[]
        def manual_routes(manual):
            manual.produces(MediaType.APPLICATION_JSON_TYPE)  # <1>
            manual.executeOn(TaskExecutors.BLOCKING)  # <2>
            manual.after(lambda request, response: response.header("X-Manual", "true"))  # <3>
            manual.GET("").resources(StaticResources.classpath("functional-static/manual"))  # <4>

        routes.path("/manual", manual_routes)
        # end::group[]
# end::clazz[]
