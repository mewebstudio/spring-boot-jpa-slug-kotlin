import com.mewebstudio.springboot.jpa.slug.kotlin.DefaultSlugGenerator
import com.mewebstudio.springboot.jpa.slug.kotlin.ISlugGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DefaultSlugGeneratorTest {
    private val slugGenerator: ISlugGenerator = DefaultSlugGenerator()

    @Test
    fun `test generate with valid input`() {
        // Given
        val input = "Hello World! This is a test."

        // When
        val slug = slugGenerator.generate(input)

        // Then
        assertNotNull(slug)
        assertEquals("hello-world-this-is-a-test", slug)
    }

    @Test
    fun `test generate with input containing special characters`() {
        // Given
        val input = "Test @123! with# special$ characters%."

        // When
        val slug = slugGenerator.generate(input)

        // Then
        assertNotNull(slug)
        assertEquals("test-123-with-special-characters", slug)
    }

    @Test
    fun `test generate with multiple spaces and hyphens`() {
        // Given
        val input = "Hello   World---This is   a test"

        // When
        val slug = slugGenerator.generate(input)

        // Then
        assertNotNull(slug)
        assertEquals("hello-world-this-is-a-test", slug)
    }

    @Test
    fun `test generate with null input`() {
        // Given
        val input: String? = null

        // When
        val slug = slugGenerator.generate(input)

        // Then
        assertNull(slug)
    }

    @Test
    fun `test generate with empty input`() {
        // Given
        val input = ""

        // When
        val slug = slugGenerator.generate(input)

        // Then
        assertEquals("", slug)
    }

    @Test
    fun `test generate with only spaces`() {
        // Given
        val input = "   "

        // When
        val slug = slugGenerator.generate(input)

        // Then
        assertEquals("-", slug)
    }
}
