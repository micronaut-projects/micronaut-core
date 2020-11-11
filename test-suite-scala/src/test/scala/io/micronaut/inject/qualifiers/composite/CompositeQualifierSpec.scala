package io.micronaut.inject.qualifiers.composite

import io.micronaut.context.{DefaultBeanContext, Qualifier}
import io.micronaut.inject.qualifiers.Qualifiers
import org.assertj.core.api.Assertions.assertThat
import javax.inject.Singleton
import javax.inject.Named
import org.junit.jupiter.api.Test

@Singleton
@Named("thread") class Runner extends Runnable {
  override def run(): Unit = {
  }
}

class CompositeQualifierSpec {

  @Test
  def `test using a composite qualifier`():Unit = {
    val context = new DefaultBeanContext().start()

    val qualifier:Qualifier[AnyRef] = Qualifiers.byQualifiers(Qualifiers.byType(classOf[Runnable]), Qualifiers.byName("thread"))

    assertThat(context.getBeanDefinitions(qualifier)).hasSize(1)

    context.close()
  }
}
