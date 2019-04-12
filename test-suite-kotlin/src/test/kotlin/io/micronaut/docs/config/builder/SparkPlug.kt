package io.micronaut.docs.config.builder

internal data class SparkPlug(
        val name: String?,
        val type: String?,
        val companyName: String?
) {
    override fun toString(): String {
        return "${type ?: ""}(${companyName ?: ""} ${name ?: ""})"
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }

    data class Builder(
            var name: String? = "4504 PK20TT",
            var type: String? = "Platinum TT",
            var companyName: String? = "Denso"
    ) {
        fun withName(name: String?): Builder {
            this.name = name
            return this
        }

        fun withType(type: String?): Builder {
            this.type = type
            return this
        }

        fun withCompany(companyName: String?): Builder {
            this.companyName = companyName
            return this
        }

        fun build(): SparkPlug {
            return SparkPlug(name, type, companyName)
        }
    }

}