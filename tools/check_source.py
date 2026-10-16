#!/usr/bin/env python3
"""Run SDK-independent checks. Android compilation/device tests remain required."""
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STUDIO = ROOT / "app/src/main/java/com/termux/app/studio"
JAVA = sorted(STUDIO.glob("*.java")) + [
    ROOT / "app/src/main/java/com/termux/app/TermuxActivity.java",
    ROOT / "app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java",
    ROOT / "app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java",
]

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)

with tempfile.TemporaryDirectory(prefix="terminal-studio-check-") as directory:
    run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8", "-d", directory,
        STUDIO / "WorkspacePaths.java", ROOT / "tools/checks/WorkspacePathsCheck.java")
    run("java", "-cp", directory, "WorkspacePathsCheck")
    run("java", ROOT / "tools/checks/ParseJava.java", *JAVA)

resources = ET.parse(ROOT / "app/src/main/res/values/strings_studio.xml").getroot()
known = {(node.tag, node.attrib["name"]) for node in resources}
known.update(("drawable", path.stem) for path in (ROOT / "app/src/main/res/drawable").glob("studio_*.xml"))
for path in JAVA:
    for kind, name in re.findall(r"R\.(string|plurals|drawable)\.(studio_[a-z_]+)", path.read_text()):
        assert (kind, name) in known, f"Unresolved resource {kind}/{name}: {path}"
for path in (ROOT / "app/src/main/res").rglob("studio_*.xml"):
    ET.parse(path)
print("Studio resource XML and resource references passed.")
available = set()
for source_root in ROOT.glob("*/src/main/java"):
    for source in source_root.rglob("*.java"):
        match = re.search(r"^package\s+([\w.]+);", source.read_text(), re.MULTILINE)
        if match:
            available.add(match.group(1) + "." + source.stem)
for source in STUDIO.glob("*.java"):
    for name in re.findall(r"^import\s+(com\.termux\.[\w.]+);", source.read_text(), re.MULTILINE):
        if name != "com.termux.R":
            assert name in available, f"Unresolved repository import {name}: {source}"
print("Studio repository imports resolve to existing source files.")
print("These checks do not compile Android code or validate device rendering.")
