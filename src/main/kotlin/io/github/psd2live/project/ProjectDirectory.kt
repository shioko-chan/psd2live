package io.github.psd2live.project

import kotlinx.serialization.json.*
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.BasicFileAttributes

/** One opened project owns one directory. A crash never makes recovery data disposable. */
internal class ProjectDirectory private constructor(
    val root: Path,
    var archive: Path,
    private var savedIdentity: String?,
    private val channel: FileChannel,
    private val lock: FileLock,
) {
    private var closed = false
    var retain = false

    fun ownsSource(source: String?): Boolean = source != null &&
        Path.of(source).toAbsolutePath().normalize().startsWith(root)

    /** Called only after the save has completed and the archive was verified. */
    @Synchronized
    fun saved(path: Path) {
        archive = path.toAbsolutePath().normalize()
        savedIdentity = identity(archive)
        retain = false
        writeMarker("open")
    }

    @Synchronized
    fun close(clean: Boolean) {
        if (closed) return
        // Missing/replaced archives, failed persistence, or unsaved edits keep their source and store.
        val discard = clean && !retain && savedIdentity != null && savedIdentity == identity(archive)
        try {
            writeMarker(if (discard) "discarded" else "recovery")
        } finally {
            closed = true
            lock.release()
            channel.close()
        }
        if (discard) ProjectArchive.deleteOwnedDirectory(root, root.parent)
        else System.err.println("PSD2Live recovery retained: ${root.resolve(MARKER)}")
    }

    private fun writeMarker(state: String) = ProjectArchive.writeJsonAtomically(root.resolve(MARKER), buildJsonObject {
        put("version", 1); put("archive", archive.toString()); put("state", state)
    })

    companion object {
        const val MARKER = "session.psd2live-recovery"
        /** Recovery stores belong to the workspace, never to the operating system's temporary directory. */
        fun parent(): Path = Path.of(System.getProperty("psd2live.project.sessions")
            ?: Path.of(System.getProperty("user.home"), ".psd2live", "project-sessions").toString())
            .toAbsolutePath().normalize().also { Files.createDirectories(it) }

        private fun identity(path: Path): String? = runCatching {
            val a = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!a.isRegularFile) null else "${a.fileKey()}|${a.size()}|${a.lastModifiedTime()}"
        }.getOrNull()

        fun adopt(root: Path, archive: Path): ProjectDirectory {
            val owned = acquire(root, archive)
            try { owned.writeMarker("open"); return owned }
            catch (failure: Throwable) { owned.lock.release(); owned.channel.close(); throw failure }
        }

        fun recover(marker: Path): ProjectDirectory {
            val root = marker.toAbsolutePath().normalize().parent
            // Lock before reading the marker: another process may be closing this session.
            val owned = acquire(root, root.resolve("unavailable"))
            try {
                val data = ProjectArchive.readJson(root.resolve(MARKER))
                require(data["version"]?.jsonPrimitive?.int == 1 && data["state"]?.jsonPrimitive?.content != "discarded") {
                    "Not a recoverable project session"
                }
                owned.archive = Path.of(data.getValue("archive").jsonPrimitive.content)
                require(owned.archive.isAbsolute) { "Recovery archive path must be absolute" }
                owned.savedIdentity = null // Recovery must be explicitly saved before any automatic removal.
                owned.retain = true
                return owned
            } catch (failure: Throwable) {
                owned.lock.release(); owned.channel.close(); throw failure
            }
        }

        private fun acquire(root: Path, archive: Path): ProjectDirectory {
            val path = root.toAbsolutePath().normalize()
            require(path.parent == parent() && path.fileName.toString().startsWith("psd2live-project-") &&
                Files.isDirectory(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path.resolve(".owner.lock"))) {
                "Recovery directory is outside the project session store"
            }
            val channel = FileChannel.open(path.resolve(".owner.lock"), CREATE, WRITE)
            try {
                val lock = channel.tryLock() ?: error("Project recovery is already open")
                return ProjectDirectory(path, archive.toAbsolutePath().normalize(), identity(archive), channel, lock)
            } catch (failure: Throwable) { channel.close(); throw failure }
        }
    }
}
