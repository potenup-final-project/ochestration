package com.pg.ochestration.domain.service

import com.pg.ochestration.domain.model.FailureCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class FailureClassifierTest {
    private val classifier = FailureClassifier()

    @Test
    fun `business code maps to non retryable business`() {
        assertEquals(FailureCategory.NON_RETRYABLE_BUSINESS, classifier.classify("CARD_LIMIT_EXCEEDED"))
    }

    @Test
    fun `technical code maps to retryable technical`() {
        assertEquals(FailureCategory.RETRYABLE_TECHNICAL, classifier.classify("PG_TIMEOUT"))
    }

    @Test
    fun `unknown code maps to unknown`() {
        assertEquals(FailureCategory.UNKNOWN, classifier.classify("SOME_NEW_TOSS_CODE"))
    }
}
