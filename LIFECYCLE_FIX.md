# Opened-project directory lifecycle (pinned runtime maintenance branch)

This branch targets `b7d88cde71c44bb758e7a3f582b6e43609956de8`, the older runtime API.
It is **not a patch for current upstream master**. Upstream master at
`8f1e0e719107695c23a2e802f2cb5682baa7b24b` has replaced ProjectSession and
ViewModelAgentWorkspace with ProjectRepository, WorkspaceHost, and
WorkspaceProjectResources, including release and orphan-reclamation paths.
Do not submit this branch unchanged as a PR against that master.

## Behavior

- Save staging and archive verification remain short-lived OS temporary directories.
- An opened project's mutable store lives in `~/.psd2live/project-sessions/`.
  Override with the absolute workspace location in `-Dpsd2live.project.sessions=...`.
- Clean close releases only this session's explicitly owned directory, after its
  persistence queue finishes and its saved archive still has the recorded file
  identity, size and modification time. This is an identity guard, not a full
  cryptographic revalidation of external archive edits.
- Unsaved edits, persistence failure/timeout, replaced/missing archives, and crash
  recovery are retained. Nothing scans or removes historical or unowned directories.
- To resume, use Open Project and select `session.psd2live-recovery` in the retained
  session directory. Recovery opens in place, takes an exclusive ownership lock,
  and remains dirty until explicitly saved. The source and workspace store stay
  together; no whole-project backup copy is made during close.
- A normal JVM shutdown invokes the same bounded close path. Abrupt termination
  cannot run cleanup; already-persisted session data remains outside `/tmp`.
  In-memory edits that have not reached persistence cannot be recovered after a
  hard crash. Historical directories are not automatically migrated or removed.

## Bounded validation

```sh
python3 tools/check-project-lifecycle.py --cache "$GRADLE_USER_HOME/caches/modules-2/files-2.1"
```

Uses existing Kotlin/serialization/coroutine dependencies only. Does not download,
run Gradle, load PSD models, or invoke rendering. Compiles real ProjectArchive,
ProjectDirectory and ProjectSession. The session checks substitute a tiny
view-model/store adapter; they are not a full UI end-to-end test.

Checks 20 repeated opens/closes, corrupt archive, install failure/cancellation,
save/open/save/close, failed save preservation, unsaved recovery, abrupt child
process exit, replaced archive, concurrent owner refusal, and symlink/unowned
root refusal. Fixtures and compiler output are scoped to an automatically removed
folder in the checkout.

The changed workspace and file-picker sources were also compiled against an
existing runtime classpath. This checks integration signatures, not full rendering
or GUI behavior. The installed application's runtime pin and bridge manifest have
not been changed by this branch; publishing it does not deploy it.
