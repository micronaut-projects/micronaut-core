package io.micronaut.inject.qualifiers.compose

import io.micronaut.context.BeanContext
import org.junit.Assert
import org.junit.jupiter.api.Test
import javax.inject.{Inject, Singleton}

trait Thing {
  def getNumber: Int
}

@Composable class FirstThing extends Thing {
  override def getNumber = 1
}

@Composable class SecondThing extends Thing {
  override def getNumber = 2
}

@Singleton class ThirdThing extends Thing {
  override def getNumber = 3
}

@Composes(classOf[Thing])
@Inject
class CompositeThing(@Composable val things: Array[Thing] ) extends Thing {
  override def getNumber: Int =  things.map(_.getNumber).sum
}

class CompositionTest {
  @Test
  def testComposition(): Unit = {
    try {
      val context = BeanContext.run
      try {
        val result = context.getBean(classOf[Thing]).getNumber
        Assert.assertEquals("Should have resolved 3 candidates for annotation qualifier", 3, result)
      } finally if (context != null) context.close()
    }
  }
}
