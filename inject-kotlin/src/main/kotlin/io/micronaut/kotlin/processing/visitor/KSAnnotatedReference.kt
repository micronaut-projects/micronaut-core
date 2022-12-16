package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*
import io.micronaut.core.reflect.ReflectionUtils.findMethod

open class KSAnnotatedReference(open val nativeType: Any, val node: KSNode) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KSAnnotatedReference) return false

        if (nativeType != other.nativeType) return false

        return true
    }

    override fun hashCode(): Int {
        return nativeType.hashCode()
    }

    companion object Helper {
        fun resolveNativeType(nativeType: Any, kind: String): Any {
            val javaClass = nativeType.javaClass
            val method = findMethod(javaClass, "getKt$kind")
                .orElseGet {
                    findMethod(javaClass, "getPsi").orElseGet {
                        findMethod(javaClass, "getDescriptor").orElse(null)
                    }
                }

            return if (method != null && method.canAccess(nativeType)) {
                method.invoke(nativeType)
            } else {
                nativeType
            }
        }
    }
}

class KSClassReference(
    private val nt: KSClassDeclaration
) : KSAnnotatedReference(resolveNativeType(nt, "ClassOrObject"), nt), KSClassDeclaration by nt {
    override fun toString(): String {
        return "Class(${nt.qualifiedName?.asString()})"
    }
}

class KSValueParameterReference(
    private val nt: KSValueParameter
) : KSAnnotatedReference(resolveNativeType(nt, "Parameter"), nt), KSValueParameter by nt {
    override fun toString(): String {
        return "Parameter(${nt.name?.asString()})"
    }
}

class KSPropertyReference(
    private val nt: KSPropertyDeclaration
) : KSAnnotatedReference(resolveNativeType(nt, "Property"), nt), KSPropertyDeclaration by nt {
    override fun toString(): String {
        return "Property(${nt.qualifiedName?.asString()})"
    }
}

class KSPropertySetterReference(
    private val nt: KSPropertySetter
) : KSAnnotatedReference(resolveNativeType(nt, "PropertySetter"), nt), KSPropertySetter by nt {
    override fun toString(): String {
        return "PropertySetter(${nt.receiver.qualifiedName?.asString()})"
    }
}

class KSPropertyGetterReference(
    private val nt: KSPropertyGetter
) : KSAnnotatedReference(resolveNativeType(nt, "PropertyGetter"), nt), KSPropertyGetter by nt {
    override fun toString(): String {
        return "PropertyGetter(${nt.receiver.qualifiedName?.asString()})"
    }
}


class KSFunctionReference(
    private val nt: KSFunctionDeclaration
) : KSAnnotatedReference(resolveNativeType(nt, "Function"), nt), KSFunctionDeclaration by nt {
    override fun toString(): String {
        return "Function(${nt.qualifiedName?.asString()})"
    }
}

