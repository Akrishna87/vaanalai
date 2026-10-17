package io.github.akrishna87.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtTextTest {
    @Test fun searchTermsDropExtras() {
        assertEquals("A.R. Rahman Jai Ho", ArtText.searchTerms("A.R. Rahman - Jai Ho (From \"Slumdog Millionaire\")"))
        assertEquals("Dua Lipa DaBaby Levitating", ArtText.searchTerms("Dua Lipa feat. DaBaby - Levitating [Remix]"))
        assertEquals("இளையராஜா இளைய நிலா", ArtText.searchTerms("இளையராஜா - இளைய நிலா"))
    }

    @Test fun searchTermsSkipNonSongs() {
        assertNull(ArtText.searchTerms("Visit www.radiocity.in"))
        assertNull(ArtText.searchTerms("Radio Mirchi", "Radio Mirchi"))
        assertNull(ArtText.searchTerms("-"))
        assertNull(ArtText.searchTerms("Advert break"))
    }

    @Test fun matchesNeedsTitleAndArtist() {
        assertTrue(ArtText.matches("Arijit Singh - Tum Hi Ho", "Arijit Singh", "Tum Hi Ho"))
        assertTrue(ArtText.matches("The Weeknd - Blinding Lights", "The Weeknd", "Blinding Lights (Radio Edit)"))
        assertTrue(ArtText.matches("Tum Hi Ho by Arijit", "Arijit Singh", "Tum Hi Ho"))
        assertFalse(ArtText.matches("Arijit Singh - Tum Hi Ho", "Some Cover Band", "Tum Hi Ho"))
        assertFalse(ArtText.matches("Arijit Singh - Tum Hi Ho", "Arijit Singh", "Channa Mereya"))
    }

    @Test fun biggerArtwork() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/x/600x600bb.jpg",
            ArtText.biggerArtwork("https://is1-ssl.mzstatic.com/image/thumb/Music/x/100x100bb.jpg"),
        )
    }

    @Test fun iconLinksBestFirst() {
        val html = """
            <html><head>
            <link rel="icon" href="/favicon.ico" sizes="16x16">
            <link rel='icon' type="image/png" sizes="192x192" href='/img/icon-192.png'>
            <link rel="apple-touch-icon" href="https://cdn.example.com/touch.png">
            <link rel=icon href=/logo.svg>
            <meta property="og:image" content="//cdn.example.com/share.jpg">
            </head></html>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://cdn.example.com/touch.png",
                "https://www.example.com/img/icon-192.png",
                "https://cdn.example.com/share.jpg",
                "https://www.example.com/apple-touch-icon.png",
            ),
            ArtText.iconLinks(html, "https://www.example.com/live/"),
        )
    }
}
