package io.micronaut.scala

import scala.tools.nsc.Global

abstract sealed class ScalaElement(val obj:Any) {
  override def hashCode(): Int = obj.hashCode()

  override def equals(that: Any): Boolean =
    this.getClass == that.getClass && this.obj.equals(that.asInstanceOf[ScalaElement].obj)
}

case class ScalaSymbolElement(symbol:Global#Symbol) extends ScalaElement(symbol)
case class ScalaNameElement(annOwner: Global#Symbol, name:Global#Name) extends ScalaElement(name)

