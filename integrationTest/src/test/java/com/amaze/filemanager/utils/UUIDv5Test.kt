package com.amaze.filemanager.utils

import org.junit.Test
import java.util.UUID

class UUIDv5Test {
    @Test
    fun testGenerate() {
        UUIDv5.fromString(UUID.randomUUID(), "Test")
        println("Current password: ${System.getProperty("CURRENT_PASSWORD")}")
    }
}
