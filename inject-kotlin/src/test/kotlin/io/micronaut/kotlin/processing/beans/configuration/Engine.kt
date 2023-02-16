package io.micronaut.kotlin.processing.beans.configuration

class Engine(val manufacturer: String) {

    class Builder {
        private var manufacturer = "Ford"

        fun withManufacturer(manufacturer: String): Builder {
            this.manufacturer = manufacturer
            return this
        }

        fun build(): Engine {
            return Engine(manufacturer)
        }
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }
}
