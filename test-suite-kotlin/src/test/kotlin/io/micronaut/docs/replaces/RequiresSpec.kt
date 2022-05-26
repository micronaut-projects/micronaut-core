package io.micronaut.docs.replaces

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.requires.Book

class RequiresSpec : StringSpec({

    "test bean replaces" {
        val applicationContext = ApplicationContext.run()
        applicationContext.getBean(BookService::class.java).shouldBeInstanceOf<MockBookService>()
        applicationContext.getBean(Book::class.java).title.shouldBe("An OK Novel")
        applicationContext.getBean(TextBook::class.java).title.shouldBe("Learning 305")

        applicationContext.close()
    }
})
