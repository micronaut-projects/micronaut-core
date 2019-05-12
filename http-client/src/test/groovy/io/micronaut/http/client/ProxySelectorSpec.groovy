package io.micronaut.http.client

import com.stehno.ersatz.ErsatzServer
import io.micronaut.http.HttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.extras.SelfSignedMitmManager
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(5)
class ProxySelectorSpec extends Specification {

    def proxyPort = 18080

    @AutoCleanup ErsatzServer server
    @AutoCleanup("stop") HttpProxyServer proxyServer

    def "Proxy is used if proxy selector provide one - no ssl" () {
        given: "http server"
        server = new ErsatzServer()
        server.expectations {
            get("/ping") {
                responder {
                    body("pong!",'text/plain')
                }
            }
        }

        and: "a non ssl proxy"
        def proxyCalled = false
        proxyServer = DefaultHttpProxyServer.bootstrap()
            .withPort(proxyPort)
            .withFiltersSource(new HttpFiltersSourceAdapter() {
                @Override
                HttpFilters filterRequest(io.netty.handler.codec.http.HttpRequest originalRequest, ChannelHandlerContext ctx) {
                    return new HttpFiltersAdapter(originalRequest) {
                        @Override
                        HttpResponse proxyToServerRequest(HttpObject httpObject) {
                            proxyCalled = true
                            return super.proxyToServerRequest(httpObject)
                        }
                    }
                }
            })
            .start()

        and: "a client"
        def config = new DefaultHttpClientConfiguration()
        def client = new DefaultHttpClient(new URL(server.httpUrl), config)

        when: "proxy selector is set to return a proxy"
        config.setProxySelector(new ProxySelector() {
            @Override
            List<Proxy> select(URI uri) {
                [new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proxyPort))]
            }

            @Override
            void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // not used
            }
        })

        then: "the proxy is being used"
        def response = client.exchange(HttpRequest.GET("/ping"), String).blockingFirst()
        response.body() == "pong!"
        proxyCalled

        cleanup:
        server.close()
    }

    def "Proxy is used if proxy selector provide one - ssl" () {
        given: "https server"
        server = new ErsatzServer({
            https()

            expectations {
                get("/ping") {
                    responder {
                        body("pong!", 'text/plain')
                    }
                }
            }
        })

        and: "a ssl proxy"
        proxyServer = DefaultHttpProxyServer.bootstrap()
            .withManInTheMiddle(new SelfSignedMitmManager())
            .withPort(proxyPort)
            .withFiltersSource(new HttpFiltersSourceAdapter() {
                @Override
                HttpFilters filterRequest(io.netty.handler.codec.http.HttpRequest originalRequest, ChannelHandlerContext ctx) {
                    return new HttpFiltersAdapter(originalRequest) {
                        @Override
                        HttpObject proxyToClientResponse(HttpObject httpObject) {
                            if(httpObject instanceof DefaultHttpResponse) {
                                def response = ((DefaultHttpResponse) httpObject)
                                response.headers().add("proxied", "true")
                                return response
                            } else {
                                return httpObject
                            }
                        }
                    }
                }
            })
            .start()

        and: "a client"
        def config = new DefaultHttpClientConfiguration()
        def client = new DefaultHttpClient(new URL(server.httpsUrl), config)

        when: "proxy selector is set to return a proxy"
        config.setProxySelector(new ProxySelector() {
            @Override
            List<Proxy> select(URI uri) {
                [new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proxyPort))]
            }

            @Override
            void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // not used
            }
        })

        then: "the proxy is being used"
        def response = client.exchange(HttpRequest.GET("/ping"), String).blockingFirst()
        response.header("proxied") == "true"
        response.body() == "pong!"

        cleanup:
        server.close()
    }
}
