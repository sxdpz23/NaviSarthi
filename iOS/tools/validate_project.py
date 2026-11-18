"""Structural checks only. This does NOT compile or type-check Swift."""
from pathlib import Path
import hashlib
import json
import plistlib
import re
import xml.etree.ElementTree as ET
import subprocess
import sys
import generate_project

ROOT = Path(__file__).resolve().parents[1]

def validate():
    project_path = ROOT / "NaviSarthi.xcodeproj/project.pbxproj"
    before = project_path.read_bytes()
    expected = generate_project.generate(write=False)
    # Do not regenerate on validation: it would erase packages installed later by Xcode.
    assert before.replace(b"\r\n",b"\n") == expected.replace(b"\r\n",b"\n"), "Project differs from baseline; validate extended Xcode projects with xcodebuild on macOS. No files changed."
    objects = generate_project.OBJECTS
    refs = {key for key in objects}
    count = 0
    for obj in objects.values():
        if obj["isa"] == "PBXFileReference" and obj.get("sourceTree") == "SOURCE_ROOT":
            assert (ROOT / obj["path"]).is_file(), obj["path"]
            count += 1
        if obj["isa"] == "PBXBuildFile":
            assert obj.get("fileRef", obj.get("productRef")) in refs
        for field in ["buildPhases", "buildConfigurations", "packageProductDependencies", "packageReferences", "targets", "children", "files"]:
            for ref in obj.get(field, []): assert ref in refs, (field, ref)
    for name in ["Info.plist", "NaviSarthi.entitlements", "PrivacyInfo.xcprivacy"]:
        with (ROOT / "Configuration" / name).open("rb") as file: plistlib.load(file)
    for name in ["Info.plist", "Share.entitlements"]:
        with (ROOT / "ShareExtension" / name).open("rb") as file: plistlib.load(file)
    ET.parse(ROOT / "NaviSarthi.xcodeproj/xcshareddata/xcschemes/NaviSarthi.xcscheme")
    sources = list((ROOT / "Sources").rglob("*.swift"))
    assert len([p for p in sources if "@main" in p.read_text(encoding="utf-8")]) == 1
    assert (ROOT / "Package.swift").is_file() and (ROOT / "Tests/CoreTests.swift").is_file()
    # Verify the one navigation session and absence of private credentials in packaged source.
    assert sum("final class NavigationSession" in p.read_text(encoding="utf-8") for p in sources) == 1
    for path in sources:
        text = path.read_text(encoding="utf-8")
        assert not re.search(r"-----BEGIN (?:RSA )?PRIVATE KEY-----|sk-proj-", text), path
    syntax = "not run: install tree-sitter and tree-sitter-swift into .validation"
    if (ROOT / ".validation/tree_sitter").is_dir():
        subprocess.run([sys.executable,str(ROOT / "tools/check_swift_syntax.py")],check=True)
        syntax = "passed: Swift grammar only, not compiler or type checking"
    result = {"structuralChecks":"passed", "referencedFiles":count, "swiftSourceFiles":len(sources),
              "swiftCompilation":"not run: Swift/Xcode unavailable on Windows",
              "swiftSyntax":syntax,
              "swiftTests":"authored, not executed", "simulator":"not run", "signing":"not run", "hardware":"not run",
              "shareTargetConfigured":"pending receiving-Mac setup", "googleOAuthConfigured":"pending rider client ID", "screenOffProjection":"experimental; not device verified",
              "coreTestCasesAuthored":len(re.findall(r"func test",(ROOT / "Tests/CoreTests.swift").read_text(encoding="utf-8"))),
              "sourceSha256":{str(p.relative_to(ROOT)).replace("\\", "/"):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(sources + list((ROOT / "Tests").rglob("*.swift")) + [ROOT / "Package.swift"])}}
    (ROOT / "VERIFICATION.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k:v for k,v in result.items() if k != "sourceSha256"}, indent=2))

if __name__ == "__main__": validate()
