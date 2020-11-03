package org.atinject.tck.auto

import javax.inject.Inject
import org.atinject.tck.auto.accessories.Cupholder

@Inject
class DriversSeat(cupholder: Cupholder) extends Seat (cupholder)