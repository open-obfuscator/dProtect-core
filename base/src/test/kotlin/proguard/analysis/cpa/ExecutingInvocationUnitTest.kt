package proguard.analysis.cpa

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import proguard.classfile.ClassConstants.TYPE_JAVA_LANG_STRING
import proguard.classfile.ClassConstants.TYPE_JAVA_LANG_STRING_BUILDER
import proguard.classfile.util.ClassUtil
import proguard.evaluation.ExecutingInvocationUnit
import proguard.evaluation.value.BasicValueFactory.UNKNOWN_VALUE
import proguard.evaluation.value.IdentifiedReferenceValue
import proguard.evaluation.value.ParticularReferenceValue
import proguard.evaluation.value.ParticularValueFactory
import proguard.evaluation.value.ParticularValueFactory.ReferenceValueFactory
import proguard.evaluation.value.ReferenceValue
import proguard.evaluation.value.TypedReferenceValue
import proguard.evaluation.value.UnknownIntegerValue
import proguard.evaluation.value.UnknownReferenceValue
import proguard.evaluation.value.Value
import proguard.testutils.ClassPoolBuilder.Companion.libraryClassPool
import proguard.testutils.findMethod

private val javaLangString = libraryClassPool.getClass("java/lang/String")
private val javaLangStringBuilder = libraryClassPool.getClass("java/lang/StringBuilder")
private val valueFactory = ParticularValueFactory(ReferenceValueFactory())
private val invocationUnit = ExecutingInvocationUnit(valueFactory)
private fun Int.toValue(): Value =
    valueFactory.createIntegerValue(this.toInt())

private fun Any?.toValue(id: Int? = null): Value = when (this) {
    null -> valueFactory.createReferenceValueNull()
    else -> when (id) {
        null -> valueFactory.createReferenceValue(libraryClassPool.getClass(ClassUtil.internalClassName(this.javaClass.canonicalName)), this)
        else -> valueFactory.createReferenceValue(libraryClassPool.getClass(ClassUtil.internalClassName(this.javaClass.canonicalName)), id, this)
    }
}
private fun UnknownString(): ReferenceValue =
    valueFactory.createReferenceValue("Ljava/lang/String;", javaLangString, false, false)

class ExecutingInvocationUnitTest : FreeSpec({
    "String method tests" - {
        val length = javaLangString.findMethod("length")
        val concat = javaLangString.findMethod("concat")
        "Unknown reference String length" {
            invocationUnit.executeMethod(javaLangString, length, UnknownReferenceValue()) shouldBe UnknownIntegerValue()
            invocationUnit.executeMethod(javaLangString, length, UnknownString()) shouldBe UnknownIntegerValue()
        }

        "Unknown String length" {
            invocationUnit.executeMethod(javaLangString, length, UNKNOWN_VALUE) shouldBe UnknownIntegerValue()
            invocationUnit.executeMethod(javaLangString, length, UnknownString()) shouldBe UnknownIntegerValue()
        }

        "Particular string length" {
            invocationUnit.executeMethod(javaLangString, length, "Hello".toValue()) shouldBe 5.toValue()
        }

        "Concat Hello with World" {
            val result = invocationUnit.executeMethod(javaLangString, concat, "Hello".toValue(), " World".toValue())
            result.shouldBeInstanceOf<ParticularReferenceValue>()
            result.referenceValue().value() shouldBe "Hello World"
        }

        "Concat Hello with unknown string" {
            val result = invocationUnit.executeMethod(javaLangString, concat, "Hello".toValue(), UnknownString())
            result.shouldBeInstanceOf<TypedReferenceValue>()
            result.shouldNotBeInstanceOf<ParticularReferenceValue>()
            result.internalType() shouldBe TYPE_JAVA_LANG_STRING
        }
    }

    "StringBuilder tests" - {

        "StringBuilder multiple appends to same ID" {
            val stringBuilder = StringBuilder().toValue() as ParticularReferenceValue
            val result = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("append"), stringBuilder, "Hello".toValue())

            result.shouldBeInstanceOf<ParticularReferenceValue>()
            result.id shouldBe stringBuilder.id
            result.value().toString() shouldBe "Hello"

            val result2 = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("append"), result.value().toValue(stringBuilder.id), " World".toValue())
            result2.shouldBeInstanceOf<ParticularReferenceValue>()
            result2.internalType() shouldBe TYPE_JAVA_LANG_STRING_BUILDER
            result2.id shouldBe stringBuilder.id

            val result3 = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("toString"), result2.value().toValue(stringBuilder.id))
            result3.shouldBeInstanceOf<ParticularReferenceValue>()
            result3.id shouldNotBe stringBuilder.id
            result3.value().shouldBeInstanceOf<String>()
            result3.value() shouldBe "Hello World"
        }

        "StringBuilder with unknown instance calling append" {
            val stringBuilder = valueFactory.createReferenceValue(TYPE_JAVA_LANG_STRING_BUILDER, javaLangStringBuilder, false, false) as IdentifiedReferenceValue
            val result = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("append"), stringBuilder, "Hello".toValue())

            result.shouldNotBeInstanceOf<ParticularReferenceValue>()
            result.shouldBeInstanceOf<IdentifiedReferenceValue>()
            result.internalType() shouldBe TYPE_JAVA_LANG_STRING_BUILDER
            // The same ID should be returned
            result.id shouldBe stringBuilder.id
            result.value() shouldBe null
        }

        "StringBuilder with unknown instance calling length" {
            val stringBuilder = valueFactory.createReferenceValue(TYPE_JAVA_LANG_STRING_BUILDER, javaLangStringBuilder, false, false) as IdentifiedReferenceValue
            val result = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("length"), stringBuilder, "Hello".toValue())

            result.shouldNotBeInstanceOf<ParticularReferenceValue>()
            result.shouldBeInstanceOf<UnknownIntegerValue>()
            result.internalType() shouldBe "I"
        }

        "StringBuilder with unknown instance calling substring" {
            val stringBuilder = valueFactory.createReferenceValue(TYPE_JAVA_LANG_STRING_BUILDER, javaLangStringBuilder, false, false) as IdentifiedReferenceValue
            val result = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("substring"), stringBuilder, 1.toValue())

            result.shouldNotBeInstanceOf<ParticularReferenceValue>()
            result.shouldBeInstanceOf<IdentifiedReferenceValue>()
            result.internalType() shouldBe TYPE_JAVA_LANG_STRING
            // The same ID should be not returned
            result.id shouldNotBe stringBuilder.id
            result.value() shouldBe null
        }

        "StringBuilder with unknown parameter" {
            val stringBuilder = StringBuilder().toValue() as ParticularReferenceValue
            val result = invocationUnit.executeMethod(javaLangStringBuilder, javaLangStringBuilder.findMethod("append"), stringBuilder, UNKNOWN_VALUE)

            result.shouldBeInstanceOf<IdentifiedReferenceValue>()
            result.id shouldBe stringBuilder.id
            result.internalType() shouldBe TYPE_JAVA_LANG_STRING_BUILDER
        }
    }
})