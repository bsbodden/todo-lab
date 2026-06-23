package dev.kmpilot.todo.runtime

import kotlinx.serialization.Serializable

/**
 * The owned, serializable statechart IR (doc-09's keystone). The minimap/editor is a VIEW projection over
 * this — never a competing source. It's published by the running app (so the diagram is the real machine's,
 * not a hand-authored file in the studio), and the studio renders it with ELK + JointJS.
 */
@Serializable
data class ChartSpec(
    val id: String,
    val initial: String,
    val states: List<StateSpec>,
    val transitions: List<TransitionSpec>,
)

@Serializable
data class StateSpec(val id: String, val kind: String) // kind drives node styling: loading/content/empty/error

@Serializable
data class TransitionSpec(val from: String, val to: String, val event: String)
