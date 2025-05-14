package com.mewebstudio.springboot.jpa.slug.kotlin

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * Annotation to mark a field in a class that should be processed for slug generation.
 *
 * This annotation is typically used to indicate which fields in an entity or model class
 * need to be automatically transformed into slugs when saved or processed.
 * It can be applied to fields that contain text-based data that should be converted into a slug
 * (e.g., a title or name field).
 *
 * Example usage:
 * <pre>
 *     class BlogPost {
 *         {@literal @}SlugField
 *         var title: String? = null
 *     }
 * </pre>
 *
 * The field marked with {@link SlugField} will be eligible for slug generation based on
 * the configured [ISlugGenerator] or slugging strategy.
 *
 * This annotation is retained at runtime, allowing reflection-based slug generation or
 * processing to take place at runtime.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class SlugField
