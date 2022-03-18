package io.micronaut.kotlin.processing.inject.configuration

class Engine private constructor(val manufacturer: String) {

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }

     class Builder {
        private var manufacturer = "Ford";

        fun withManufacturer(manufacturer: String): Builder {
            this.manufacturer = manufacturer
            return this
        }

        fun build(): Engine {
            return Engine(manufacturer)
        }
    }
}
