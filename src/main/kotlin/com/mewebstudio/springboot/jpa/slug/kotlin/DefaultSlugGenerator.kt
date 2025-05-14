package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Default implementation of the [ISlugGenerator] interface that generates slugs by transforming
 * the input string to a standardized format.
 *
 * This implementation converts the input string to lowercase, removes any non-alphanumeric characters
 * (except for spaces and hyphens), replaces consecutive spaces or hyphens with a single hyphen,
 * and ensures the final slug has no extra hyphens.
 *
 * **Example:**
 * ```
 * val generator = DefaultSlugGenerator()
 * val slug = generator.generate("Hello World! This is a test.")
 * println(slug) // Output: "hello-world-this-is-a-test"
 * ```
 */
class DefaultSlugGenerator : ISlugGenerator {
    /**
     * Generates a slug from the given input string.
     *
     * This method processes the input string by:
     * - Converting the string to lowercase
     * - Removing any characters that are not alphanumeric, spaces, or hyphens
     * - Replacing one or more spaces with a single hyphen
     * - Ensuring that multiple hyphens are replaced by a single hyphen
     *
     * @param input the input string to generate a slug from. May be `null`.
     * @return the generated slug, or `null` if the input is `null`.
     */
    override fun generate(input: String?): String? = input?.lowercase()
        ?.replace(Regex("[^a-z0-9\\s-]"), "")
        ?.replace(Regex("\\s+"), "-")
        ?.replace(Regex("-+"), "-")
}
