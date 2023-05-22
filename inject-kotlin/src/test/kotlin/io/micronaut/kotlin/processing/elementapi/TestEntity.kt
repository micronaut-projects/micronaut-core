package io.micronaut.kotlin.processing.elementapi

import jakarta.validation.constraints.*
import javax.persistence.*

@Entity
class TestEntity(
    @Column(name="test_name") var name: String,
    @Size(max=100) var age: Int,
    primitiveArray: Array<Int>) {

    @Id
    @GeneratedValue
    var id: Long? = null

    @Version
    var version: Long? = null

    private var primitiveArray: Array<Int>? = null

    private var v: Long? = null

    @Version
    fun getAnotherVersion(): Long? {
        return v;
    }

    fun setAnotherVersion(v: Long) {
        this.v = v
    }
}
