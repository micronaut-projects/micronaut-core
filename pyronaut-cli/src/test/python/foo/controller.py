from micronaut.http.annotation import Controller, Get

@Controller("/hello")
class HelloController:
    @Get(produces = "text/plain")
    def index(self) -> str:
        return "Hello World"

@Controller("/")
class HomeController:
    @Get(produces = "text/plain")
    def index(self) -> str:
        return "Home controller"
