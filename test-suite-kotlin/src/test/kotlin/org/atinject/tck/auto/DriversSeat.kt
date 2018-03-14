package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.Cupholder

import javax.inject.Inject

open class DriversSeat @Inject
constructor(cupholder: Cupholder) : Seat(cupholder)
