package io.micronaut.http.client

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.AttributeKey
import spock.lang.Shared
import spock.lang.Specification


class ConnectTTLHandlerSpec extends Specification{

  @Shared
  AttributeKey key = AttributeKey.newInstance("RELEASE_CHANNEL")

  def "RELEASE_CHANNEL should be true for those channels who's connect-ttl is reached"(){

    setup:
    MockChannel channel = new MockChannel();
    ChannelHandlerContext context = Mock()

    when:
    new ConnectTTLHandler(1,key).handlerAdded(context)
    channel.runAllPendingTasks()

    then:
    _ * context.channel() >> channel

    channel.attr(key)

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
