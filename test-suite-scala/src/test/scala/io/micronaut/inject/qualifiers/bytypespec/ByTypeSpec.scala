package io.micronaut.inject.qualifiers.bytypespec

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Type
import javax.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


trait Foo

@Singleton class One extends Foo

@Singleton class Two extends Foo

@Singleton class Three extends Foo

@Singleton class Bean(@Type(Array(classOf[One], classOf[Two])) val foos: Array[Foo])

class ByTypeSpec {
  @Test
  def `test by type qualifier injection`():Unit = {
    val context = new DefaultBeanContext().start()

    val b = context.getBean(classOf[Bean])

    assertThat(b.foos.exists(_.isInstanceOf[One])).isTrue
    assertThat(b.foos.exists(_.isInstanceOf[Two])).isTrue
    assertThat(b.foos.exists(_.isInstanceOf[Three])).isFalse
  }
}
