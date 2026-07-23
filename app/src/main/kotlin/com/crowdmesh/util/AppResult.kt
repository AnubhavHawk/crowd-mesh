package com.crowdmesh.util

/** Lightweight success/failure wrapper for use-case results, with a typed [Failure] reason instead of exceptions. */
sealed interface AppResult<out T, out F> {
    data class Success<T>(val value: T) : AppResult<T, Nothing>
    data class Failure<F>(val reason: F) : AppResult<Nothing, F>
}

inline fun <T, F> AppResult<T, F>.onSuccess(block: (T) -> Unit): AppResult<T, F> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T, F> AppResult<T, F>.onFailure(block: (F) -> Unit): AppResult<T, F> {
    if (this is AppResult.Failure) block(reason)
    return this
}
