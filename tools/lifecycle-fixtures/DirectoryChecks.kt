package io.github.psd2live.project

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Runs against the real ProjectArchive/ProjectDirectory, with byte-sized fixtures, no model or renderer. */
fun main(args: Array<String>) {
    val base = Path.of(args.first())
    val sessions = base.resolve("sessions")
    System.setProperty("psd2live.project.sessions", sessions.toString())
    val archive = base.resolve("saved.psd2live")
    if (args.getOrNull(1) == "crash") {
        val lease = ProjectDirectory.adopt(ProjectArchive.extract(archive, ProjectDirectory.parent()), archive)
        Files.writeString(lease.root.resolve("unsaved.txt"), "persisted before crash")
        Files.writeString(base.resolve("crash-root.txt"), lease.root.toString())
        Runtime.getRuntime().halt(0)
    }
    val input = Files.createTempDirectory("psd2live-project-")
    try {
        Files.createDirectories(input.resolve("source"))
        Files.writeString(input.resolve("source/original.psd"), "tiny fixture source")
        Files.writeString(input.resolve("workspace.json"), "{}")
        ProjectArchive.write(input, archive, "test")
    } finally { ProjectArchive.deleteTemporaryDirectory(input) }
    fun opened(): ProjectDirectory = ProjectDirectory.adopt(ProjectArchive.extract(archive, ProjectDirectory.parent()), archive)
    repeat(20) { opened().close(clean = true) }
    check(Files.list(sessions).use { it.count() } == 0L) { "Clean sessions accumulate" }
    println("PASS repeated open-close: 20 cycles, zero retained directories")
    val dirty = opened()
    Files.writeString(dirty.root.resolve("workspace.json"), "{\"unique\":true}")
    dirty.close(clean = false)
    check(Files.exists(dirty.root.resolve("source/original.psd")))
    val recovery = ProjectDirectory.recover(dirty.root.resolve(ProjectDirectory.MARKER))
    check(ProjectArchive.readJson(recovery.root.resolve("workspace.json"))["unique"] == JsonPrimitive(true))
    recovery.close(clean = true) // Merely reopening recovery never makes unsaved data disposable.
    check(Files.isDirectory(recovery.root))
    println("PASS unsaved close, reopen, and recovery source preservation")
    val saved = ProjectDirectory.recover(dirty.root.resolve(ProjectDirectory.MARKER))
    val target = base.resolve("recovered.psd2live")
    Files.writeString(target, "independently saved bytes")
    saved.saved(target)
    saved.close(clean = true)
    check(!Files.exists(saved.root) && Files.isRegularFile(target))
    println("PASS explicit successful save permits recovery release")
    val child = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("java.class.path"), "io.github.psd2live.project.DirectoryChecksKt",
        base.toString(), "crash").inheritIO().start()
    check(child.waitFor() == 0)
    val crashed = Path.of(Files.readString(base.resolve("crash-root.txt")))
    val crashedRecovery = ProjectDirectory.recover(crashed.resolve(ProjectDirectory.MARKER))
    check(Files.readString(crashed.resolve("unsaved.txt")) == "persisted before crash")
    crashedRecovery.close(clean = false)
    println("PASS abrupt process exit retains independently reopenable recovery")
    val replaced = opened()
    Files.writeString(archive, "externally replaced")
    replaced.close(clean = true)
    check(Files.isDirectory(replaced.root))
    println("PASS changed archive keeps recovery")
    val before = Files.list(sessions).use { it.count() }
    check(runCatching { ProjectArchive.extract(archive, sessions) }.isFailure)
    check(Files.list(sessions).use { it.count() } == before)
    println("PASS corrupt open removes only its failed extraction")
    val resume = ProjectDirectory.recover(replaced.root.resolve(ProjectDirectory.MARKER))
    check(runCatching { ProjectDirectory.recover(resume.root.resolve(ProjectDirectory.MARKER)) }.isFailure)
    resume.close(clean = false)
    println("PASS concurrent recovery refuses second owner")
    val outside = Files.createDirectories(base.resolve("unowned"))
    check(runCatching { ProjectDirectory.adopt(outside, archive) }.isFailure)
    check(Files.isDirectory(outside))
    println("PASS unowned directory never adopted or deleted")
    val link = sessions.resolve("psd2live-project-link")
    Files.createSymbolicLink(link, outside)
    check(runCatching { ProjectDirectory.adopt(link, archive) }.isFailure)
    check(Files.isDirectory(outside))
    println("PASS symbolic-link root refused")
}
