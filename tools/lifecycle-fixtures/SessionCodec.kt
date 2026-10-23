package io.github.psd2live.project
import io.github.psd2live.ui.state.State
import kotlinx.serialization.json.*
object WorkspaceStateCodec {
    fun encode(state: State) = buildJsonObject { put("logEntries", JsonArray(emptyList())) }
    fun decode(value: JsonObject) = State()
}
