package io.micronaut.docs.ioc.validation.custom

import javax.inject.Singleton
import javax.validation.constraints.NotBlank
import java.time.Duration

// tag::class[]
@Singleton
open class HolidayService {

    open fun startHoliday( @NotBlank person: String,
                           @DurationPattern duration: String): String {
        val d = Duration.parse(duration)
        val mins = d.toMinutes()
        return "Person $person is off on holiday for $mins minutes"
    }
}
// end::class[]
