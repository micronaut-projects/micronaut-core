package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected

@ConfigurationProperties("team")
@Introspected
class TeamConfiguration  {
    var name: String? = null
    var color: String? = null
    var playerNames: List<String>? = null
    @ConfigurationBuilder(prefixes = ["with"], configurationPrefix = "team-admin") // <3>
    var builder = TeamAdmin.Builder() // <4>
}

data class TeamAdmin(
    val manager: String?,
    val coach: String?,
    val president: String?) { // <1>
    data class Builder( // <2>
        var manager: String? = null,
        var coach: String? = null,
        var president: String? = null) {
        fun withManager(manager: String) = apply { this.manager = manager } // <3>
        fun withCoach(coach: String) = apply { this.coach = coach }
        fun withPresident(president: String) = apply { this.president = president }
        fun build() = TeamAdmin(manager, coach, president) // <4>
    }
}
