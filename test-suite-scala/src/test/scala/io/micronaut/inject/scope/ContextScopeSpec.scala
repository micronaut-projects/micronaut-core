package io.micronaut.inject.scope

import io.micronaut.context.{BeanRegistration, DefaultBeanContext}
import io.micronaut.context.annotation.Context
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Context
class A {

}

class ContextScopeSpec {

  private def fieldForObject(obj:AnyRef, fieldName:String) = {
    obj.getClass.getDeclaredFields.toList.find{ fld => fld.setAccessible(true); fld.getName.equals(fieldName)}
      .get.get(obj)
  }

  @Test
  def `test context scope`(): Unit = {
    val beanContext = new DefaultBeanContext().start()

    assertThat(fieldForObject(beanContext, "singletonObjects").asInstanceOf[java.util.Map[_, BeanRegistration[_]]]
        .values().stream().anyMatch(_.getBean.isInstanceOf[A])).isTrue
  }
}
