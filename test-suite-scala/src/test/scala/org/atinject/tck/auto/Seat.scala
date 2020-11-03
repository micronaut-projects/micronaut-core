package org.atinject.tck.auto

import javax.inject.{Inject, Singleton}
import org.atinject.tck.auto.accessories.Cupholder

@Singleton
@Inject
class Seat(val cupholder: Cupholder) {
  def getCupholder: Cupholder = cupholder
}