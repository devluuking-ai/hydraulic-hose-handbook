package com.example

import com.example.data.DefaultChapters
import com.example.model.Chapter
import com.example.model.PendingUpload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun defaultChapters_countAndContent() {
        val chapters = DefaultChapters.get()
        assertEquals(5, chapters.size)

        // Chapter 1: Introduction
        val ch1 = chapters.find { it.id == "chapter_1_intro" }
        assertNotNull(ch1)
        assertTrue(ch1!!.title.contains("Introduction"))
        assertEquals("img_hose_intro", ch1.thumbnail)
        assertTrue(ch1.sections.isNotEmpty())

        // Chapter 2: Hose Types
        val ch2 = chapters.find { it.id == "chapter_2_types" }
        assertNotNull(ch2)
        assertTrue(ch2!!.title.contains("Types"))
        assertEquals("img_hose_types", ch2.thumbnail)

        // Chapter 3: Fittings
        val ch3 = chapters.find { it.id == "chapter_3_fittings" }
        assertNotNull(ch3)
        assertTrue(ch3!!.title.contains("Fittings"))
        assertEquals("img_hose_fittings", ch3.thumbnail)

        // Chapter 4: Installation
        val ch4 = chapters.find { it.id == "chapter_4_install" }
        assertNotNull(ch4)
        assertTrue(ch4!!.title.contains("Installation"))
        assertEquals("img_hose_install", ch4.thumbnail)

        // Chapter 5: Maintenance and Safety
        val ch5 = chapters.find { it.id == "chapter_5_safety" }
        assertNotNull(ch5)
        assertTrue(ch5!!.title.contains("Maintenance") || ch5.title.contains("Safety"))
        assertEquals("img_hose_safety", ch5.thumbnail)
    }

    @Test
    fun driveFolderIdExtraction_tests() {
        fun extract(input: String): String {
            val trimmed = input.trim()
            if (trimmed.contains("/folders/")) {
                return trimmed.substringAfter("/folders/").substringBefore("?").substringBefore("/")
            }
            if (trimmed.contains("id=")) {
                return trimmed.substringAfter("id=").substringBefore("&")
            }
            return trimmed
        }

        assertEquals("1aBcDeFgHiJk", extract("1aBcDeFgHiJk"))
        assertEquals("1aBcDeFgHiJk", extract(" https://drive.google.com/drive/folders/1aBcDeFgHiJk?usp=sharing "))
        assertEquals("1aBcDeFgHiJk", extract("https://drive.google.com/open?id=1aBcDeFgHiJk"))
    }

    @Test
    fun pinLockoutLogic_simulation() {
        var failedAttempts = 0
        var lockoutUntil = 0L
        val correctPin = "1234"

        fun verify(input: String, now: Long): Boolean {
            if (now < lockoutUntil) return false
            if (input == correctPin) {
                failedAttempts = 0
                return true
            } else {
                failedAttempts++
                if (failedAttempts >= 3) {
                    lockoutUntil = now + 30_000L
                }
                return false
            }
        }

        val t0 = 1000000L
        // 1st wrong attempt
        assertFalse(verify("0000", t0))
        assertEquals(1, failedAttempts)

        // 2nd wrong attempt
        assertFalse(verify("1111", t0 + 1000))
        assertEquals(2, failedAttempts)

        // 3rd wrong attempt -> triggers 30s lockout
        assertFalse(verify("2222", t0 + 2000))
        assertEquals(3, failedAttempts)
        assertEquals(t0 + 32000L, lockoutUntil)

        // Even with correct PIN, must be rejected during 30s lockout
        assertFalse(verify("1234", t0 + 15000))

        // After 30s expires, correct PIN is accepted and resets failed attempts
        assertTrue(verify("1234", t0 + 33000))
        assertEquals(0, failedAttempts)
    }

    @Test
    fun pendingUploads_modelVerification() {
        val upload = PendingUpload(
            id = "test-123",
            action = "UPSERT",
            articleId = "art-456",
            title = "Test Article",
            localImageName = "art_test_1.jpg",
            localSecondImageName = "art_test_2.jpg"
        )
        assertEquals("UPSERT", upload.action)
        assertEquals("art-456", upload.articleId)
        assertEquals("art_test_1.jpg", upload.localImageName)
        assertEquals("art_test_2.jpg", upload.localSecondImageName)
    }

    @Test
    fun chapter_twoImagesSupport() {
        // 0 images
        val ch0 = Chapter(id = "c0", title = "No Images")
        assertEquals(null, ch0.thumbnail)
        assertEquals(null, ch0.secondImage)

        // 1 image
        val ch1 = Chapter(id = "c1", title = "One Image", thumbnail = "img1.jpg")
        assertEquals("img1.jpg", ch1.thumbnail)
        assertEquals(null, ch1.secondImage)

        // 2 images
        val ch2 = Chapter(id = "c2", title = "Two Images", thumbnail = "img1.jpg", secondImage = "img2.jpg")
        assertEquals("img1.jpg", ch2.thumbnail)
        assertEquals("img2.jpg", ch2.secondImage)
    }
}
