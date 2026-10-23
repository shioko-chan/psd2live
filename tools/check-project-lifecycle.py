#!/usr/bin/env python3
"""Compile only the lifecycle code with an existing Kotlin cache; never fetch or build the application."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--cache', type=Path, required=True, help='Existing Gradle caches/modules-2/files-2.1')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
def jars(group, name, version=None):
    paths = sorted((args.cache / group / name).glob(f'{version or "*"}/*/*.jar'))
    if not paths:
        raise SystemExit(f'Missing existing dependency: {group}/{name}; nothing downloaded')
    return [str(paths[-1])]
compiler = sum((jars(g, n) for g, n in [
    ('org.jetbrains.kotlin', 'kotlin-compiler-embeddable'),
    ('org.jetbrains.kotlin', 'kotlin-stdlib'),
    ('org.jetbrains.kotlin', 'kotlin-script-runtime'),
    ('org.jetbrains.kotlin', 'kotlin-reflect'),
    ('org.jetbrains.kotlin', 'kotlin-daemon-embeddable'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm'),
    ('org.jetbrains', 'annotations'),
]), [])
libs = sum((jars(g, n) for g, n in [
    ('org.jetbrains.kotlin', 'kotlin-stdlib'),
    ('org.jetbrains.kotlinx', 'kotlinx-serialization-json-jvm'),
    ('org.jetbrains.kotlinx', 'kotlinx-serialization-core-jvm'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm'),
]), [])
# Stay inside the working checkout; all new compiler/test artifacts are bounded and automatically removed.
with tempfile.TemporaryDirectory(prefix='.lifecycle-check-', dir=root) as directory:
    path = Path(directory)
    tmp = path / 'tmp'
    tmp.mkdir()
    out = path / 'classes'
    source = root / 'src/main/kotlin/io/github/psd2live/project'
    subprocess.run(['java', '-Xmx512m', f'-Djava.io.tmpdir={tmp}', '-cp', os.pathsep.join(compiler),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                    '-classpath', os.pathsep.join(libs), '-d', str(out),
                    str(source / 'ProjectArchive.kt'), str(source / 'ProjectDirectory.kt'),
                    str(source / 'ProjectSession.kt'),
                    *map(str, sorted((root / 'tools/lifecycle-fixtures').glob('*.kt')))], check=True)
    fixture = path / 'fixture'
    fixture.mkdir()
    subprocess.run(['java', '-Xmx128m', f'-Djava.io.tmpdir={tmp}', '-cp', os.pathsep.join([str(out), *libs]),
                    'io.github.psd2live.project.DirectoryChecksKt', str(fixture)], check=True)
    subprocess.run(['java', '-Xmx128m', f'-Djava.io.tmpdir={tmp}', '-cp', os.pathsep.join([str(out), *libs]),
                    'io.github.psd2live.project.SessionChecksKt', str(fixture)], check=True)
    assert not list(tmp.iterdir()), 'Temporary extraction or save-verification directory leaked'
    print('PASS no JVM temporary directories left; fixture and compiler outputs removed on exit')
