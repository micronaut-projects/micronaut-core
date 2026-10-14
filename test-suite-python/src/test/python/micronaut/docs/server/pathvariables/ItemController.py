from micronaut.context.annotation import Requires

# tag::imports[]
import java
from micronaut.http import PathVariables
from micronaut.http.annotation import Controller, Get

Integer = java.type("java.lang.Integer")
# end::imports[]


@Requires(property="spec.name", value="ItemControllerSpec")
# tag::class[]
@Controller
class ItemController:
# end::class[]

    # tag::item[]
    @Get("/items/{id}{/page}")  # <1>
    def item(self, pathVariables: PathVariables) -> str:  # <2>
        item_id = pathVariables.getLong("id")  # <3>
        page = pathVariables.getInt("page", 1)  # <4>
        return f"Item {item_id}, page {page}"
    # end::item[]

    # tag::lists[]
    @Get("/tags/{tags}")
    def tags(self, pathVariables: PathVariables) -> str:
        tags = pathVariables.getStrings("tags")  # <1>
        return "Tags [" + ", ".join(tags) + "]"

    @Get("/sum/{numbers}")
    def total(self, pathVariables: PathVariables) -> str:
        numbers = pathVariables.getList("numbers", Integer)  # <2>
        return f"Sum {sum(numbers)}"
    # end::lists[]

# tag::endclass[]
# end::endclass[]
