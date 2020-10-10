package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.Cupholder
import javax.inject.{Inject, Singleton}

@Singleton
@Inject
class Seat(val cupholder: Cupholder) {
  def getCupholder: Cupholder = cupholder
}