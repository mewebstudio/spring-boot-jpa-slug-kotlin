package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Matches valid Java/JPA identifiers — ensures entity and field names are safe for JPQL.
 */
val SAFE_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
