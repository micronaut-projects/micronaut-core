from micronaut.http.annotation import Controller, Get

@Controller
class MyController:
    @Get(value="/", produces="text/plain")
    def index(self) -> str:
        return "Hello World!"

    @Get(value="/hello")
    def hello(self) -> dict:
        return {"Hello": "Pyronaut!"}
