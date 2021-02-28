package dev.skye.daisy

import kotlin.test.Test
import kotlin.test.assertTrue

class LibraryTest {

    @Test fun `test library method`() {
        val sut = Library()

        val result = sut.someLibraryMethod()

        assertTrue(result)
    }
}
