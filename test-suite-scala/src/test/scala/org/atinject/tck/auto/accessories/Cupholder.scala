package org.atinject.tck.auto.accessories

import javax.inject.{Inject, Provider, Singleton}
import org.atinject.tck.auto.Seat

@Singleton
@Inject
class Cupholder(val seatProvider: Provider[Seat])
