package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Retry
class ConnectionTTLSpec extends Specification {

  @Shared
  @AutoCleanup
  EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
          'spec.name': 'ConnectionTTLSpec'
  ])

  def "should close connection according to connect-ttl"() {
    setup:
    ApplicationContext clientContext = ApplicationContext.run(
      'my.port':embeddedServer.getPort(),
      'micronaut.http.client.connect-ttl':'1000ms',
      'micronaut.http.client.pool.enabled':true
    )
    HttpClient httpClient = clientContext.createBean(HttpClient, embeddedServer.getURL())

    when:"make first request"
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)
    Channel ch = getQueuedChannels(httpClient).get(0)

    then:"ensure that connection is open as connect-ttl is not reached"
    getQueuedChannels(httpClient).size() == 1
    ch.isOpen()

    when:"make another request after connect-ttl is exceeded"
    Thread.sleep(1100)
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)

    then:"ensure channel is closed"
    new PollingConditions().eventually {
      !ch.isOpen()
    }

    cleanup:
    httpClient.close()
    clientContext.close()
  }

  def "shouldn't close connection if connect-ttl is not set"() {
    setup:
    ApplicationContext clientContext = ApplicationContext.run(
      'my.port':embeddedServer.getPort(),
      'micronaut.http.client.pool.enabled':true
    )
    HttpClient httpClient = clientContext.createBean(HttpClient, embeddedServer.getURL())

    when:"make first request"
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)
    List<Channel> deque = getQueuedChannels(httpClient)

    then:"ensure that connection is open as connect-ttl is not reached"
    new PollingConditions().eventually {
      deque.get(0).isOpen()
    }

    when:"make another request after some time"
    Thread.sleep(1100)
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)

    then:"ensure channel is still open"
    new PollingConditions().eventually {
      deque.get(0).isOpen()
    }

    cleanup:
    httpClient.close()
    clientContext.close()
  }

  def "shouldn't close connection before ttl expires"() {
    setup:
    ApplicationContext clientContext = ApplicationContext.run(
        'my.port':embeddedServer.getPort(),
        'micronaut.http.client.pool.enabled':true,
        'micronaut.http.client.connect-ttl':'5000ms',
    )
    HttpClient httpClient = clientContext.createBean(HttpClient, embeddedServer.getURL())

    when:"make first request"
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)
    Collection<Channel> deque = getQueuedChannels(httpClient)

    then:"ensure that connection is open as connect-ttl is not reached"
    new PollingConditions().eventually {
      deque.get(0).isOpen()
    }

    when:"make another request"
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)

    then:"ensure channel is still open"
    new PollingConditions().eventually {
      deque.get(0).isOpen()
    }

    cleanup:
    httpClient.close()
    clientContext.close()
  }

  def "should close connection according to connect-ttl when health check on release"() {
    setup:
    ApplicationContext clientContext = ApplicationContext.run(
        'my.port':embeddedServer.getPort(),
        'micronaut.http.client.connect-ttl':'1000ms',
        'micronaut.http.client.pool.enabled':true
    )
    HttpClient httpClient = clientContext.createBean(HttpClient, embeddedServer.getURL())

    when:"make first request"
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/'),String)
    Channel ch = getQueuedChannels(httpClient).get(0)

    then:"ensure that connection is open as connect-ttl is not reached"
    getQueuedChannels(httpClient).size() == 1
    ch.isOpen()

    when:"make another request after connect-ttl is exceeded"
    httpClient.toBlocking().retrieve(HttpRequest.GET('/connectTTL/slow'),String)

    then:"ensure channel is closed"
    new PollingConditions().eventually {
      !ch.isOpen()
    }

    cleanup:
    httpClient.close()
    clientContext.close()
  }

  List<Channel> getQueuedChannels(HttpClient client) {
    return client.connectionManager.channels
  }

  @Requires(property = 'spec.name', value = 'ConnectionTTLSpec')
  @Controller('/connectTTL')
  static class GetController {

    @Get(value = "/", produces = MediaType.TEXT_PLAIN)
    String get() {
      return "success"
    }

    @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
    String getSlow() {
      sleep 1100
      return "success"
    }
  }

}
