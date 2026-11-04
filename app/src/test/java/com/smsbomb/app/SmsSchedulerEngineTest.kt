package com.smsbomb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsSchedulerEngineTest {

    private class FakeClock(var now: Long = 0L) {
        fun advance(millis: Long) {
            now += millis
        }
    }

    private class FakeSender : SmsSender {
        val calls = mutableListOf<Pair<String, String>>()
        var nextResult: SendResult? = null

        override fun send(phoneNumber: String, message: String): SendResult {
            calls += phoneNumber to message
            return nextResult ?: SendResult(phoneNumber, message, 0L, success = true)
        }
    }

    private fun engine(
        clock: FakeClock,
        sender: FakeSender,
        intervalMillis: Long = 60_000L,
        onSend: (SendResult) -> Unit = {},
    ) = SmsSchedulerEngine(
        config = SendConfig("+49123", "ping", intervalMillis),
        sender = sender,
        clock = { clock.now },
        onSend = onSend,
    )

    @Test
    fun `tick before start does nothing`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender)
        assertNull(engine.onTick())
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun `first send happens after one interval`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender, intervalMillis = 60_000L)
        engine.start()

        clock.advance(59_999L)
        assertNull(engine.onTick())
        assertTrue(sender.calls.isEmpty())

        clock.advance(1L)
        val result = engine.onTick()
        assertEquals(1, sender.calls.size)
        assertEquals("+49123" to "ping", sender.calls.first())
        assertEquals(true, result?.success)
    }

    @Test
    fun `repeated ticks send at each interval`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender, intervalMillis = 10_000L)
        engine.start()

        repeat(5) {
            clock.advance(10_000L)
            engine.onTick()
        }
        assertEquals(5, sender.calls.size)
        assertEquals(5, engine.sentCount)
    }

    @Test
    fun `sent count increments per send`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender)
        engine.start()
        clock.advance(60_000L)
        engine.onTick()
        clock.advance(60_000L)
        engine.onTick()
        assertEquals(2, engine.sentCount)
    }

    @Test
    fun `stop prevents further sends`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender)
        engine.start()
        engine.stop()
        clock.advance(600_000L)
        assertNull(engine.onTick())
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun `millisUntilNextSend counts down`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender, intervalMillis = 60_000L)
        assertNull(engine.millisUntilNextSend())
        engine.start()
        assertEquals(60_000L, engine.millisUntilNextSend())
        clock.advance(15_000L)
        assertEquals(45_000L, engine.millisUntilNextSend())
    }

    @Test
    fun `millisUntilNextSend never negative`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender)
        engine.start()
        clock.advance(999_999L)
        assertEquals(0L, engine.millisUntilNextSend())
    }

    @Test
    fun `late tick does not cause catch-up burst`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender, intervalMillis = 10_000L)
        engine.start()

        // Simulate a badly delayed tick: 10 intervals late.
        clock.advance(100_000L)
        engine.onTick()
        assertEquals(1, sender.calls.size)

        // Next send is a full interval away from "now", not immediately due again.
        assertEquals(10_000L, engine.millisUntilNextSend())
        assertNull(engine.onTick())
        assertEquals(1, sender.calls.size)
    }

    @Test
    fun `failed send still counts and schedules next`() {
        val clock = FakeClock()
        val sender = FakeSender().apply {
            nextResult = SendResult("+49123", "ping", 0L, success = false, error = "no radio")
        }
        val engine = engine(clock, sender, intervalMillis = 10_000L)
        engine.start()
        clock.advance(10_000L)

        val result = engine.onTick()
        assertFalse(result!!.success)
        assertEquals("no radio", result.error)
        assertEquals(0, engine.sentCount)
        assertEquals(10_000L, engine.millisUntilNextSend())
    }

    @Test
    fun `onSend callback is invoked`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val received = mutableListOf<SendResult>()
        val engine = engine(clock, sender) { received += it }
        engine.start()
        clock.advance(60_000L)
        engine.onTick()
        assertEquals(1, received.size)
    }

    @Test
    fun `lastResult is updated`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender)
        assertNull(engine.lastResult)
        engine.start()
        clock.advance(60_000L)
        val result = engine.onTick()
        assertEquals(result, engine.lastResult)
    }

    @Test
    fun `restart after stop reschedules cleanly`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = engine(clock, sender, intervalMillis = 10_000L)
        engine.start()
        clock.advance(10_000L)
        engine.onTick()
        engine.stop()

        clock.advance(10_000L)
        engine.start()
        assertEquals(10_000L, engine.millisUntilNextSend())
        assertNull(engine.onTick())
        clock.advance(10_000L)
        engine.onTick()
        assertEquals(2, sender.calls.size)
    }

    @Test
    fun `random factor adds a bounded delay`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val config = SendConfig(
            phoneNumber = "+49123",
            message = "ping",
            intervalMillis = 600_000L,
            randomFactorPercent = 50,
        )
        val engine = SmsSchedulerEngine(
            config = config,
            sender = sender,
            clock = { clock.now },
            randomUnit = { 0.5 },
        )

        engine.start()

        // 50% of 10 minutes, multiplied by a 0.5 sample, adds 150,000 ms.
        assertEquals(750_000L, engine.millisUntilNextSend())
    }

    @Test
    fun `random factor never schedules earlier than the base interval`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = SmsSchedulerEngine(
            config = SendConfig("+49123", "ping", 600_000L, randomFactorPercent = 100),
            sender = sender,
            clock = { clock.now },
            randomUnit = { 0.0 },
        )

        engine.start()
        assertEquals(600_000L, engine.millisUntilNextSend())
    }

    @Test
    fun `end time stops before a later send`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = SmsSchedulerEngine(
            config = SendConfig("+49123", "ping", 600_000L, endAtMillis = 300_000L),
            sender = sender,
            clock = { clock.now },
        )
        engine.start()

        assertEquals(300_000L, engine.millisUntilNextEvent())
        clock.advance(300_000L)

        assertNull(engine.onTick())
        assertEquals(SmsSchedulerEngine.StopReason.END_TIME, engine.stopReason)
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun `sms count limit stops after the configured number`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = SmsSchedulerEngine(
            config = SendConfig("+49123", "ping", 600_000L, maxSmsCount = 2),
            sender = sender,
            clock = { clock.now },
            randomUnit = { 0.0 },
        )
        engine.start()

        repeat(2) {
            clock.advance(600_000L)
            engine.onTick()
        }

        assertEquals(2, sender.calls.size)
        assertEquals(2, engine.sentCount)
        assertFalse(engine.running)
        assertNull(engine.millisUntilNextEvent())
        assertEquals(SmsSchedulerEngine.StopReason.MAX_SMS_COUNT, engine.stopReason)
    }

    @Test
    fun `end time is checked even if no sms is due`() {
        val clock = FakeClock()
        val sender = FakeSender()
        val engine = SmsSchedulerEngine(
            config = SendConfig("+49123", "ping", 600_000L, endAtMillis = 300_000L),
            sender = sender,
            clock = { clock.now },
        )
        engine.start()
        clock.advance(300_000L)

        assertNull(engine.onTick())
        assertFalse(engine.running)
        assertEquals(SmsSchedulerEngine.StopReason.END_TIME, engine.stopReason)
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun `failed sms does not consume the successful sms limit`() {
        val clock = FakeClock()
        val sender = FakeSender().apply {
            nextResult = SendResult("+49123", "ping", 0L, success = false, error = "no radio")
        }
        val engine = SmsSchedulerEngine(
            config = SendConfig("+49123", "ping", 600_000L, maxSmsCount = 1),
            sender = sender,
            clock = { clock.now },
            randomUnit = { 0.0 },
        )
        engine.start()
        clock.advance(600_000L)
        engine.onTick()

        assertTrue(engine.running)
        assertEquals(0, engine.sentCount)
        sender.nextResult = null
        clock.advance(600_000L)
        engine.onTick()

        assertFalse(engine.running)
        assertEquals(1, engine.sentCount)
        assertEquals(SmsSchedulerEngine.StopReason.MAX_SMS_COUNT, engine.stopReason)
    }
}
