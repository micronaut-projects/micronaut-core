# tag::class[]
from micronaut.http.annotation import FilterMatcher


@FilterMatcher  # <1>
def BasicAuth(func):
    return func
# end::class[]
