package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Strategy interface for generating slugs from input strings.
 *
 * Implementations of this interface define custom logic to convert
 * a given input string (e.g., a title or name) into a URL-friendly slug.
 *
 * This interface is intended to be used in conjunction with the
 * [EnableSlug] annotation to plug in custom slug generation behavior
 * within a Spring Boot application.
 *
 * **Example:**
 * ```
 * class CustomSlugGenerator : ISlugGenerator {
 *     override fun generate(input: String): String {
 *         return input.toLowerCase().replace("[^a-z0-9]+".toRegex(), "-").replace("^-|\$".toRegex(), "")
 *     }
 * }
 * ```
 *
 * @see EnableSlug
 */
interface ISlugGenerator {
    /**
     * Generates a slug from the given input string.
     *
     * @param input the original string (e.g., a title or name)
     * @return a URL-friendly slug
     */
    fun generate(input: String?): String?
}
