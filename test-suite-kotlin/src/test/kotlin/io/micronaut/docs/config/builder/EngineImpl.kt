package io.micronaut.docs.config.builder

// tag::class[]
internal class EngineImpl(manufacturer: String, cylinders: Int, crankShaft: CrankShaft, sparkPlug: SparkPlug) : Engine {
    override var cylinders: Int = 0
    private val manufacturer: String
    private val crankShaft: CrankShaft
    private val sparkPlug: SparkPlug

    init {
        this.crankShaft = crankShaft
        this.cylinders = cylinders
        this.manufacturer = manufacturer
        this.sparkPlug = sparkPlug
    }

    override fun start(): String {
        return "$manufacturer Engine Starting V$cylinders [rodLength=${crankShaft.rodLength ?: 6.0}, sparkPlug=$sparkPlug]"
    }

    class Builder {
        private var manufacturer = "Ford"
        private var cylinders: Int = 0
        fun withManufacturer(manufacturer: String): Builder {
            this.manufacturer = manufacturer
            return this
        }

        fun withCylinders(cylinders: Int): Builder {
            this.cylinders = cylinders
            return this
        }

        fun build(crankShaft: CrankShaft.Builder, sparkPlug: SparkPlug.Builder): EngineImpl {
            return EngineImpl(manufacturer, cylinders, crankShaft.build(), sparkPlug.build())
        }
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }
}
// end::class[]