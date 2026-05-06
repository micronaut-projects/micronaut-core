# tag::imports[]
# TODO: Re-enable when Python classes can extend DefaultRouteBuilder for programmatic routes.
# from jakarta.inject import Inject, Singleton
# from micronaut.context import ExecutionHandleLocator
# from micronaut.web.router import DefaultRouteBuilder
# end::imports[]


# tag::class[]
# TODO: Re-enable when Python DefaultRouteBuilder support is implemented.
# @Singleton
# class MyRoutes(DefaultRouteBuilder):  # <1>
#     def __init__(self,
#                  execution_handle_locator: ExecutionHandleLocator,
#                  uri_naming_strategy):
#         super().__init__(execution_handle_locator, uri_naming_strategy)
#
#     @Inject
#     def issues_routes(self, issues_controller):  # <2>
#         self.GET("/issues/show/{number}", issues_controller, "issue", int)  # <3>
# end::class[]
