package com.mewebstudio.springboot.jpa.slug.kotlin

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DefaultSlugGenerator Test Suite")
class DefaultSlugGeneratorTest {
    private val slugGenerator: ISlugGenerator = DefaultSlugGenerator()

    @Test
    @DisplayName("Should generate a slug for a given identifier")
    fun `test generate with valid input`() {
        // Given
        val input = "Hello World! This is a test."

        // When
        val slug = slugGenerator.generate(input)

        // Then
        Assertions.assertNotNull(slug)
        Assertions.assertEquals("hello-world-this-is-a-test", slug)
    }

    @Test
    @DisplayName("Should generate a slug for a given identifier")
    fun `test generate with input containing special characters`() {
        // Given
        val input = "Test @123! with# special$ characters%."

        // When
        val slug = slugGenerator.generate(input)

        // Then
        Assertions.assertNotNull(slug)
        Assertions.assertEquals("test-123-with-special-characters", slug)
    }

    @Test
    @DisplayName("Should generate a slug for a given identifier")
    fun `test generate with multiple spaces and hyphens`() {
        // Given
        val input = "Hello   World---This is   a test"

        // When
        val slug = slugGenerator.generate(input)

        // Then
        Assertions.assertNotNull(slug)
        Assertions.assertEquals("hello-world-this-is-a-test", slug)
    }

    @Test
    @DisplayName("Should generate a slug for a given identifier")
    fun `test generate with null input`() {
        // Given
        val input: String? = null

        // When
        val slug = slugGenerator.generate(input)

        // Then
        Assertions.assertNull(slug)
    }

    @Test
    @DisplayName("Should generate a slug for a given identifier")
    fun `test generate with empty input`() {
        // Given
        val input = ""

        // When
        val slug = slugGenerator.generate(input)

        // Then
        Assertions.assertEquals("", slug)
    }

    @Test
    @DisplayName("Should generate a slug for a given identifier")
    fun `test generate with only spaces`() {
        // Given
        val input = "   "

        // When
        val slug = slugGenerator.generate(input)

        // Then
        Assertions.assertEquals("-", slug)
    }
}