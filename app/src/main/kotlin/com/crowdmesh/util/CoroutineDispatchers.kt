package com.crowdmesh.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Injectable dispatcher set so tests can swap in a `TestDispatcher`.
 * Provided as a singleton by [com.crowdmesh.di.DispatcherModule].
 */
data class CoroutineDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
