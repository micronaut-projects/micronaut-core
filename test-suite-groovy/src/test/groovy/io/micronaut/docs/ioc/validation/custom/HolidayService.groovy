package io.micronaut.docs.ioc.validation.custom

import javax.inject.Singleton
import javax.validation.constraints.NotBlank
import java.time.Duration
// tag::class[]
@Singleton
class HolidayService {

    // tag::method[]
    String startHoliday(@NotBlank String person,
                        @DurationPattern String duration) {
        final Duration d = Duration.parse(duration)
        return "Person $person is off on holiday for ${d.toMinutes()} minutes"
    }
    // end::method[]
}
// end::class[]

