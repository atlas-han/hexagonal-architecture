package io.reflectoring.buckpal.common

import javax.validation.ConstraintViolationException
import javax.validation.Validation
import javax.validation.Validator

abstract class SelfValidating<T> {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    /**
     * Evaluates all Bean Validations on the attributes of this instance.
     */
    protected fun validateSelf() {
        @Suppress("UNCHECKED_CAST")
        val violations = validator.validate(this as T)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }
    }
}
