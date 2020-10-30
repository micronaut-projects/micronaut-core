package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.Cupholder
import javax.inject.Inject

@Inject
class DriversSeat(cupholder: Cupholder) extends Seat (cupholder)