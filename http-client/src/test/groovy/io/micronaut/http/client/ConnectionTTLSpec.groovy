package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import io.netty.channel.pool.AbstractChannelPoolMap
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.lang.reflect.Field

@Retry
class ConnectionTTLSpec extends Specification {


  @Shared
  @AutoCleanup
  ApplicationContext context = ApplicationContext.run()

  @Shared
  EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()


  def "should close connection according to connect-ttl"() {
    setup:
    ApplicationContext clientContext = ApplicationContext.run(
      'my.port':embeddedServer.getPort(),
      'micronaut.http.client.connect-ttl':'1000ms',
      'micronaut.http.client.pool.enabled':true
    )
    DefaultHttpClient httpClient = clientContext.createBean(DefaultHttpClient, embeddedServer.getURL())


    when:"make first request"
    httpClient.retrieve(HttpRequest.GET('/connectTTL/'),String).blockingFirst()
    Channel ch = getQueuedChannels(httpClient).first

    then:"ensure that connection is open as connect-ttl is not reached"
    getQueuedChannels(httpClient).size() == 1
    ch.isOpen()

    when:"make another request in which connect-ttl will exceed"
    httpClient.retrieve(HttpRequest.GET('/connectTTL/slow'),String).blockingFirst()

    then:"ensure channel is closed"
    new PollingConditions().eventually {
      !ch.isOpen()
    }

    cleanup:
    httpClient.close()
    clientContext.close()
  }

  def "shouldn't close connection if connect-ttl is not passed"() {
    setup:
    ApplicationContext clientContext = ApplicationContext.run(
      'my.port':embeddedServer.getPort(),
      'micronaut.http.client.pool.enabled':true
    )
    DefaultHttpClient httpClient = clientContext.createBean(DefaultHttpClient, embeddedServer.getURL())


    when:"make first request"
    httpClient.retrieve(HttpRequest.GET('/connectTTL/'),String).blockingFirst()
    Deque<Channel> deque = getQueuedChannels(httpClient)

    then:"ensure that connection is open as connect-ttl is not reached"
    new PollingConditions().eventually {
      deque.first.isOpen()
    }

    when:"make another request "
    httpClient.retrieve(HttpRequest.GET('/connectTTL/slow'),String).blockingFirst()

    then:"ensure channel is closed"
    new PollingConditions().eventually {
      deque.first.isOpen()
    }

    cleanup:
    httpClient.close()
    clientContext.close()
  }


  Deque getQueuedChannels(RxHttpClient client) {
    AbstractChannelPoolMap poolMap = client.poolMap
    Field mapField = AbstractChannelPoolMap.getDeclaredField("map")
    mapField.setAccessible(true)
    Map innerMap = mapField.get(poolMap)
    return innerMap.values().first().deque
  }


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
