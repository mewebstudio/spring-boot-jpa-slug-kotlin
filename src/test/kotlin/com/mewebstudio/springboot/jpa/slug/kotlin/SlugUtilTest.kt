package com.mewebstudio.springboot.jpa.slug.kotlin

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.junit.jupiter.api.Test

@DisplayName("SlugUtil Test Suite")
class SlugUtilTest {
    private val slugGenerator: ISlugGenerator = mock(ISlugGenerator::class.java)

    @Test
    @DisplayName("Test generate with valid input")
    fun `test generate with valid input`() {
        // Given
        val input = "Hello World! This is a test."
        val expectedSlug = "hello-world-this-is-a-test"

        // When
        SlugUtil.setGenerator(slugGenerator)
        `when`(slugGenerator.generate(input)).thenReturn(expectedSlug)

        val generatedSlug = SlugUtil.generate(input)

        // Then
        assertNotNull(generatedSlug)
        assertEquals(expectedSlug, generatedSlug)
        verify(slugGenerator).generate(input)
    }

    @Test
    @DisplayName("Test generate with null input")
    fun `test generate with null input`() {
        // Given
        val input: String? = null

        // When
        SlugUtil.setGenerator(slugGenerator)
        `when`(slugGenerator.generate(input)).thenReturn(null)

        val generatedSlug = SlugUtil.generate(input)

        // Then
        assertNull(generatedSlug)
        verify(slugGenerator).generate(input)
    }

    @Test
    @DisplayName("Test generate with empty input")
    fun `test setGenerator with null generator`() {
        // Given
        val exception = assertThrows<SlugOperationException> {
            SlugUtil.setGenerator(null)
        }

        // Then
        assertEquals("SlugGenerator cannot be null", exception.message)
    }

    @Test
    @DisplayName("Test generate with empty input")
    fun `test getGenerator when generator is not set`() {
        // Given
        resetSlugUtil()

        // When
        val exception = assertThrows<SlugOperationException> {
            SlugUtil.getGenerator()
        }

        // Then
        assertEquals("SlugGenerator not set", exception.message)
    }

    @Test
    @DisplayName("Test generate with valid input")
    fun `test generate with empty string`() {
        // Given
        val input = ""
        val expectedSlug = ""

        // When
        SlugUtil.setGenerator(slugGenerator)
        `when`(slugGenerator.generate(input)).thenReturn(expectedSlug)

        val generatedSlug = SlugUtil.generate(input)

        // Then
        assertNotNull(generatedSlug)
        assertEquals(expectedSlug, generatedSlug)
        verify(slugGenerator).generate(input)
    }

    @Test
    @DisplayName("Test generate with valid input")
    fun `test generate with special characters input`() {
        // Given
        val input = "Hello @World! #Test$"
        val expectedSlug = "hello-world-test"

        // When
        SlugUtil.setGenerator(slugGenerator)
        `when`(slugGenerator.generate(input)).thenReturn(expectedSlug)

        val generatedSlug = SlugUtil.generate(input)

        // Then
        assertNotNull(generatedSlug)
        assertEquals(expectedSlug, generatedSlug)
        verify(slugGenerator).generate(input)
    }

    @Test
    @DisplayName("Test generate with valid input")
    fun `test generate with multiple spaces`() {
        // Given
        val input = "Hello    World     Test"
        val expectedSlug = "hello-world-test"

        // When
        SlugUtil.setGenerator(slugGenerator)
        `when`(slugGenerator.generate(input)).thenReturn(expectedSlug)

        val generatedSlug = SlugUtil.generate(input)

        // Then
        assertNotNull(generatedSlug)
        assertEquals(expectedSlug, generatedSlug)
        verify(slugGenerator).generate(input)
    }

    @Test
    @DisplayName("Test generate with invalid input")
    fun `test generate with mixed case input`() {
        // Given
        val input = "HeLLo WoRLd"
        val expectedSlug = "hello-world"

        // When
        SlugUtil.setGenerator(slugGenerator)
        `when`(slugGenerator.generate(input)).thenReturn(expectedSlug)

        val generatedSlug = SlugUtil.generate(input)

        // Then
        assertNotNull(generatedSlug)
        assertEquals(expectedSlug, generatedSlug)
        verify(slugGenerator).generate(input)
    }

    private fun resetSlugUtil() {
        val field = SlugUtil::class.java.getDeclaredField("generator")
        field.isAccessible = true
        field.set(null, null)
    }
}
