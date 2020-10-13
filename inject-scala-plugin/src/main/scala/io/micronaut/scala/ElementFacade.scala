package io.micronaut.scala

import scala.tools.nsc.Global

abstract sealed class ElementFacade

case class SymbolFacade(symbol:Global#Symbol) extends ElementFacade {
  override def hashCode(): Int = symbol.hashCode()

  override def equals(that: Any): Boolean =
    this.getClass == that.getClass && this.symbol.equals(that.asInstanceOf[SymbolFacade].symbol)
}

case class NameFacade(annOwner: Class[_], name:String) extends ElementFacade {
  override def hashCode(): Int = annOwner.hashCode() + name.hashCode()

  override def equals(that: Any): Boolean =
    this.getClass == that.getClass &&
      this.annOwner.equals(annOwner.asInstanceOf[NameFacade].annOwner) &&
      this.name.equals(annOwner.asInstanceOf[NameFacade].name)
}
