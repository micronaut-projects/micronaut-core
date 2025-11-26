# tag::annotation[]
from javax.sql import DataSource
from micronaut.context.annotation import Requires

@Requires(beans = DataSource)
@Requires(property = "datasource.url")
def RequiresJdbc():
    def decorator(func):
        return func
    return decorator
# end::annotation[]
