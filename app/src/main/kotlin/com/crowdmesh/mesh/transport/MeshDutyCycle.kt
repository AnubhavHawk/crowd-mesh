package com.crowdmesh.mesh.transport

/** Transport-agnostic duty-cycle request; each [Transport] maps this to its own radio-specific settings. */
enum class MeshDutyCycle {
    /** Right after app-open or an explicit "Update" tap. */
    ACTIVE,

    /** App open but idle — gentle background duty-cycling. */
    FOREGROUND_IDLE,

    /** A single short burst, meant to run once inside a WorkManager execution. */
    BACKGROUND_BURST,
}
