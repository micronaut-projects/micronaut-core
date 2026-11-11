from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property, Requires
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import DefaultHttpClientConfiguration, HttpClient
from micronaut.http.ssl import ClientSslConfiguration
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Assertions, Test



def client(url, verifier):
    # tag::hostnameVerifier[]
    configuration = DefaultHttpClientConfiguration()
    ssl = ClientSslConfiguration()
    ssl.setInsecureTrustAllCertificates(True)  # only for the self-signed test certificate
    ssl.setEndpointIdentification(False)
    ssl.setHostnameVerifier(verifier)  # <1>
    configuration.setSslConfiguration(ssl)
    client = HttpClient.create(url, configuration)
    # end::hostnameVerifier[]
    return client


@Requires(property="spec.name", value="HostnameVerifierSpec")
@Controller
class VerifiedController:

    @Get("/verified")
    def get(self) -> str:
        return "ok"


@Property(name="spec.name", value="HostnameVerifierSpec")
@Property(name="micronaut.server.ssl.enabled", value="true")
@Property(name="micronaut.server.ssl.build-self-signed", value="true")
@Property(name="micronaut.server.ssl.port", value="-1")
@MicronautTest
class HostnameVerifierSpec:
    server: Annotated[EmbeddedServer, Inject]

    @Test
    def hostnameVerifier(self) -> None:
        accepting = client(self.server.getURL(), lambda host, session: host == "localhost")
        try:
            Assertions.assertEquals("ok", accepting.toBlocking().retrieve("/verified"))
        finally:
            accepting.close()
        rejecting = client(self.server.getURL(), lambda host, session: False)
        try:
            failed = False
            try:
                rejecting.toBlocking().retrieve("/verified")
            except Exception:
                failed = True
            Assertions.assertTrue(failed)
        finally:
            rejecting.close()
