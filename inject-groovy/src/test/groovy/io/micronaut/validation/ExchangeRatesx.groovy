package io.micronaut.validation

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

import javax.validation.constraints.PastOrPresent
import java.time.LocalDate;

@Client("https://exchangeratesapi.io")
interface ExchangeRatesx {

    @Get("{date}")
    String rate(@PastOrPresent LocalDate date)
}
