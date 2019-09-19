package io.micronaut.http.client

import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.pool.ChannelPool
import io.netty.util.AttributeKey
import spock.lang.Shared
import spock.lang.Specification

class ConnectTTLChannelPoolSpec extends Specification{

  @Shared
  AttributeKey key = AttributeKey.newInstance("RELEASE_CHANNEL")


  def "should close the channel if RELEASE_CHANNEL is true"(){

    setup:
    ChannelPool channelPool = Mock()
    ConnectTTLChannelPool connectTTLChannelPool = new ConnectTTLChannelPool(channelPool,key)
    Channel channel = new MockChannel()
    channel.attr(key).set(true)

    when:
    connectTTLChannelPool.release(channel)
    channel.runAllPendingTasks()

    then:
    !channel.isOpen()

    cleanup:
    connectTTLChannelPool.close()

  }

  def "shouldn't close the channel if RELEASE_CHANNEL is false"(){

    setup:
    ChannelPool channelPool = Mock()
    ConnectTTLChannelPool connectTTLChannelPool = new ConnectTTLChannelPool(channelPool,key)
    Channel channel = new MockChannel()
    channel.attr(key).set(false)

    when:
    connectTTLChannelPool.release(channel)
    channel.runAllPendingTasks()

    then:
    channel.isOpen()

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


