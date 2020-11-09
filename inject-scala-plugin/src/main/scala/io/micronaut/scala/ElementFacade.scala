package io.micronaut.scala

import scala.tools.nsc.Global

abstract sealed class ElementFacade

case class SymbolFacade(symbol:Global#Symbol) extends ElementFacade {
  override def hashCode(): Int = symbol.hashCode()

  override def equals(that: Any): Boolean =
    this.getClass == that.getClass &&
      this.symbol.equals(that.asInstanceOf[SymbolFacade].symbol)
}
/* Annotation attribute are not methods in Scala */
case class SymbolNameFacade(symbol:Global#Symbol, name:String) extends ElementFacade {
  override def hashCode(): Int = symbol.hashCode() + name.hashCode()

  override def equals(that: Any): Boolean =
    this.getClass == that.getClass &&
      this.symbol.equals(that.asInstanceOf[SymbolNameFacade].symbol) &&
      this.name.equals(that.asInstanceOf[SymbolNameFacade].name)
}

case class ClassFacade(annOwner: Class[_], name:String) extends ElementFacade {
  override def hashCode(): Int = annOwner.hashCode() + name.hashCode()

  override def equals(that: Any): Boolean =
    this.getClass == that.getClass &&
      this.annOwner.equals(that.asInstanceOf[ClassFacade].annOwner) &&
      this.name.equals(that.asInstanceOf[ClassFacade].name)
}
