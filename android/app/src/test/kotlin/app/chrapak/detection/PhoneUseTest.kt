package app.chrapak.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUseTest {
    @Test
    fun `screen on and unlocked is use, with a short grace after`() {
        val use = PhoneUse(graceMs = 5_000)
        assertFalse(use.update(0, screenOn = false, locked = true))
        assertTrue(use.update(1_000, screenOn = true, locked = false))
        assertTrue(use.update(5_000, screenOn = false, locked = true)) // still within the grace
        assertFalse(use.update(6_001, screenOn = false, locked = true))
    }

    @Test
    fun `a lit lock screen, like checking the time, is not use`() {
        val use = PhoneUse()
        assertFalse(use.update(0, screenOn = true, locked = true))
    }
}
