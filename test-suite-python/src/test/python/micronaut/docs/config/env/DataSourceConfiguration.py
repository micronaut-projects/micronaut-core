from typing import Annotated

# tag::eachProperty[]
from java.net import URI

from micronaut.context.annotation import Parameter
from micronaut.context.annotation import EachProperty


@EachProperty("test.datasource")  # <1>
class DataSourceConfiguration:
    url: URI = URI("localhost")

    def __init__(self, name: Annotated[str, Parameter]):  # <2>
        self.name = name

    def get_name(self) -> str:
        return self.name

    def get_url(self) -> URI:  # <3>
        return self.url

    def set_url(self, url: URI) -> None:
        self.url = url
# end::eachProperty[]
