package io.micronaut.inject.constructor.streaminjection

import java.util.stream.Collectors

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorStreamSpec {

  @Test
  def `test injection via constructor that takes a stream`():Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b =  context.getBean(classOf[B])

    assertThat(b.all).isNotNull

    val listFromStream = b.all.collect(Collectors.toList[A])
    assertThat(listFromStream.size).isEqualTo(2)
    assertThat(listFromStream).contains(
      context.getBean(classOf[AImpl]),
      context.getBean(classOf[AnotherImpl])
    )
  }
}
