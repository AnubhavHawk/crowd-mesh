package com.crowdmesh.fakes

import com.crowdmesh.util.TimeProvider

class FakeTimeProvider(var currentMillis: Long = 1_700_000_000_000L) : TimeProvider {
    override fun nowMillis(): Long = currentMillis
}
