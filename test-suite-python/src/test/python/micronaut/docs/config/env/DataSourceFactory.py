from java.net import URI

from .DataSourceConfiguration import DataSourceConfiguration

from micronaut.context.annotation import EachBean
from micronaut.context.annotation import Factory


class DataSource:
    def __init__(self, uri: URI):
        self.uri = uri

    def connect(self):
        raise NotImplementedError("Can't really connect. I'm not a real data source")


# tag::eachBean[]
@Factory  # <1>
class DataSourceFactory:
    @EachBean(DataSourceConfiguration.__qualname__)  # <2>
    def data_source(self, configuration: DataSourceConfiguration) -> DataSource:  # <3>
        url = configuration.get_url()
        return DataSource(url)
# end::eachBean[]
