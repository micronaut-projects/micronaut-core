package io.micronaut.http.client

import io.micronaut.http.client.netty.ConnectTTLHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import spock.lang.Specification


class ConnectTTLHandlerSpec extends Specification{


  def "RELEASE_CHANNEL should be true for those channels who's connect-ttl is reached"(){

    setup:
    MockChannel channel = new MockChannel();
    ChannelHandlerContext context = Mock()

    when:
    new ConnectTTLHandler(1).handlerAdded(context)
    channel.runAllPendingTasks()

    then:
    _ * context.channel() >> channel

    channel.attr(ConnectTTLHandler.RELEASE_CHANNEL)

  }

  class MockChannel extends EmbeddedChannel {
    MockChannel() throws Exception {
      super.doRegister()
    }

    void runAllPendingTasks() throws InterruptedException {
      super.runPendingTasks()
      while (runScheduledPendingTasks() != -1) {
        Thread.sleep(1)
      }
    }
  }
}