package io.github.akrishna87.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyTest {
    @Test fun findsTypedFrequencies() {
        assertEquals("98.3", Frequency.find("98.3")?.mhz)
        assertEquals("98.3", Frequency.find("98,3 FM")?.mhz)
        assertEquals("98.3", Frequency.find("983")?.mhz)
        assertEquals("106.4", Frequency.find("1064")?.mhz)
        assertEquals("104", Frequency.find("104 fm")?.mhz)
        assertEquals("98.3", Frequency.find("983 FM")?.mhz)
        assertEquals("106.4", Frequency.find("1064 fm")?.mhz)
        assertEquals(listOf("mirchi"), Frequency.find("Mirchi 98.3")?.words)
    }

    @Test fun ignoresOtherNumbers() {
        assertNull(Frequency.find("104"))
        assertNull(Frequency.find("radio 1"))
        assertNull(Frequency.find("120.5"))
        assertNull(Frequency.find("2024"))
    }

    @Test fun matchesNamesExactly() {
        assertTrue(Frequency.inName("Radio Mirchi 98.3 FM", "98.3"))
        assertTrue(Frequency.inName("Hello FM 106,4", "106.4"))
        assertTrue(Frequency.inName("Big FM 92.7FM", "92.7"))
        assertTrue(Frequency.inName("Radio 104 FM", "104"))
        assertFalse(Frequency.inName("Radio 198.3", "98.3"))
        assertFalse(Frequency.inName("Radio 98.35", "98.3"))
        assertFalse(Frequency.inName("Radio 104.8", "104"))
    }
}
