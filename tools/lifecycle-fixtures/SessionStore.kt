package io.github.psd2live.agent
import io.github.psd2live.project.ProjectDirectory
import io.github.psd2live.ui.state.*
import java.nio.file.*

data class History(val headNodeId: String = "test-head")
class AgentWorkspaceStore(val root: Path) {
    fun persistHistory(id: String, history: History) { Files.createDirectories(root.resolve(id)); Files.writeString(root.resolve(id).resolve("HEAD.json"), history.headNodeId) }
    fun copyAuxiliary(id: String, target: Path) {}
    fun persistSpatial(id: String, key: String, spatial: String) {}
    fun persistTasks(id: String, tasks: List<String>) {}
    fun loadHistory(id: String): History? = if (Files.exists(root.resolve(id).resolve("HEAD.json"))) History() else null
}
internal class ViewModelAgentWorkspace(val vm: PSD2LiveViewModel, val store: AgentWorkspaceStore) {
    var owned: ProjectDirectory? = null
    var failInstall: Throwable? = null
    data class Capture(val projectId: String, val history: History, val uiState: State, val store: AgentWorkspaceStore,
                       val spatial: Map<String, String> = emptyMap(), val tasks: List<String> = emptyList())
    suspend fun captureProject(summary: String, actor: String) = Capture("test", History(), vm.state.value, store)
    suspend fun flushProjectPersistence() {}
    fun projectDirectorySaved(path: Path, captured: State) { owned?.saved(path) }
    suspend fun installProject(id: String, file: Path, source: Path, state: State, tree: History,
                               store: AgentWorkspaceStore, expected: State) {
        failInstall?.let { throw it }
        vm.state.value = state.copy(loadedInputPath = source.toString())
    }
    fun rememberProjectDirectory(directory: ProjectDirectory, previous: State) { owned = directory }
}
