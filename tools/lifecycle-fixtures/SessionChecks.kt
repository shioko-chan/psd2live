package io.github.psd2live.project
import io.github.psd2live.agent.*
import io.github.psd2live.ui.state.*
import kotlinx.coroutines.*
import java.nio.file.*

fun main(args: Array<String>) = runBlocking {
    val base = Path.of(args.single())
    System.setProperty("psd2live.project.sessions", base.resolve("session-store").toString())
    val psd = base.resolve("fixture.psd"); Files.writeString(psd, "small fixture")
    val vm = PSD2LiveViewModel(); vm.state.value = State(inputPath = psd.toString())
    val workspace = ViewModelAgentWorkspace(vm, AgentWorkspaceStore(base.resolve("workspace")))
    val session = ProjectSession(vm)
    val archive = base.resolve("saved.psd2live")
    session.save(workspace, archive)
    check(Files.isRegularFile(archive))
    session.open(workspace, archive)
    val first = workspace.owned!!
    check(first.root.parent == ProjectDirectory.parent())
    session.save(workspace, archive)
    first.close(clean = !vm.state.value.projectDirty)
    check(!Files.exists(first.root))
    println("PASS real ProjectSession save-open-save-close with tiny store fixture")
    for (failure in listOf(IllegalStateException("install failure"), CancellationException("cancelled install"))) {
        workspace.failInstall = failure
        check(runCatching { session.open(workspace, archive) }.isFailure)
        check(Files.list(ProjectDirectory.parent()).use { it.count() } == 0L)
    }
    workspace.failInstall = null
    println("PASS real ProjectSession install failure and cancellation clean extraction")
    val before = Files.readAllBytes(archive)
    val failing = ProjectSession(vm) { _, _, _ -> error("save failed") }
    // Reopening supplies a source path that exists, so failure happens at archive writing.
    session.open(workspace, archive)
    check(runCatching { failing.save(workspace, archive) }.isFailure)
    check(Files.readAllBytes(archive).contentEquals(before))
    val dirty = workspace.owned!!
    dirty.close(clean = false)
    session.open(workspace, dirty.root.resolve(ProjectDirectory.MARKER))
    check(vm.state.value.projectDirty)
    check(workspace.owned!!.root == dirty.root)
    workspace.owned!!.close(clean = false)
    println("PASS failed save preserves archive; recovery opens in place and remains dirty")
}
