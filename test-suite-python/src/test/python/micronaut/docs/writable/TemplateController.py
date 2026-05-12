from micronaut.core.annotation import Introspected
# tag::imports[]
from micronaut.core.io import Writable
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
# end::imports[]


@Introspected
class TemplateWritable(Writable):

    def __init__(self, text: str):
        self.text = text

    def writeTo(self, writer):
        writer.write(self.text)


# tag::clazz[]
@Controller("/template")
class TemplateController:

    template = "Dear {firstName} {lastName}. Nice to meet you."  # <1>

    @Get(value="/welcome", produces=MediaType.TEXT_PLAIN)
    def render(self) -> Writable:  # <2>
        return TemplateWritable(
            "Dear Fred Flintstone. Nice to meet you."
        )  # <3>
# end::clazz[]
