package io.github.psd2live.ui.state
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow

data class State(val loadedInputPath: String? = null, val inputPath: String = "", val projectDirty: Boolean = false)
class PSD2LiveViewModel {
    val state = MutableStateFlow(State())
    fun projectSaveStarted() {}
    fun projectSaveFailed(failure: Exception) { state.value = state.value.copy(projectDirty = true) }
    fun projectSaveFinished(path: Path, head: String, captured: State) { state.value = state.value.copy(projectDirty = false) }
    fun markProjectAuxiliaryChanged() { state.value = state.value.copy(projectDirty = true) }
}
