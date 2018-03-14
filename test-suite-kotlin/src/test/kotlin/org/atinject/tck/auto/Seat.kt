package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.Cupholder

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class Seat @Inject
internal constructor(val cupholder: Cupholder)
