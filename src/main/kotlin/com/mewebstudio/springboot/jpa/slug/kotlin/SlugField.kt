package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Marks an entity field or class as the source for slug generation.
 *
 * **Field-level (backward compatible):**
 * ```kotlin
 * @SlugField
 * var title: String? = null
 * ```
 * The annotated field's value is used as the slug source.
 *
 * **Class-level (multi-field):**
 * ```kotlin
 * @SlugField("title", "description")
 * class Article : ISlugSupport<Long> { ... }
 * ```
 * The listed field values are joined with [separator] before slug generation.
 *
 * **Dot-notation (related entity field):**
 * ```kotlin
 * @SlugField("category.title", "title")
 * class Article : ISlugSupport<Long> { ... }
 * ```
 * Traverses the object graph via reflection/getters. Null intermediate values skip that path.
 *
 * When both a class-level and a field-level `@SlugField` are present, class-level takes priority.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SlugField(
    vararg val fields: String,
    val separator: String = " "
)
