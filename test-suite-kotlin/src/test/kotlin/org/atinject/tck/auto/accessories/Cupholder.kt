package org.atinject.tck.auto.accessories

import org.atinject.tck.auto.Seat

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
open class Cupholder @Inject
constructor(val seatProvider: Provider<Seat>)
