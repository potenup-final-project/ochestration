package com.pg.ochestration.application.service

import kotlin.test.Test
import kotlin.test.assertFailsWith

class WebhookDispatchPropertiesTest {

    @Test
    fun `batchSize는 1 이상이어야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            WebhookDispatchProperties(batchSize = 0)
        }
    }

    @Test
    fun `timeout 값은 1ms 이상이어야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            WebhookDispatchProperties(connectTimeoutMs = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            WebhookDispatchProperties(readTimeoutMs = 0)
        }
    }

    @Test
    fun `connectTimeoutMs는 Int 최대값을 넘을 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            WebhookDispatchProperties(connectTimeoutMs = Int.MAX_VALUE.toLong() + 1)
        }
    }
}
