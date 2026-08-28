from urllib.parse import quote

# tag::class[]
from micronaut.context import BeanProvider
from micronaut.context.annotation import Requires
from micronaut.context.env import Environment
from micronaut.http import HttpRequest, MutableHttpRequest
from micronaut.http.annotation import ClientFilter, RequestFilter
from micronaut.http.client import HttpClient
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn


@Requires(env=Environment.GOOGLE_COMPUTE)
@ClientFilter("/google-auth/api/**")
class GoogleAuthFilter:

    def __init__(self, httpClientProvider: BeanProvider[HttpClient]):  # <1>
        self.authClientProvider = httpClientProvider

    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    def filter(self, request: MutableHttpRequest) -> None:
        uri = self.encodeURI(request)
        token = self.authClientProvider.get().toBlocking().retrieve(
            HttpRequest.GET(uri).header("Metadata-Flavor", "Google")  # <2>
        )
        request.bearerAuth(token)

    def encodeURI(self, request: MutableHttpRequest) -> str:
        fullURI = request.getUri()
        receivingURI = fullURI.getScheme() + "://" + fullURI.getHost()
        return "http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=" + quote(receivingURI, safe="")
# end::class[]
