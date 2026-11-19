"""Package only this iOS source tree into the standing project delivery directory."""
from pathlib import Path
import hashlib
import json
import zipfile
import validate_project

ROOT = Path(__file__).resolve().parents[1]
DELIVERY = ROOT.parent / "builds and versions" / "iOS source"
SKIP = {".build", ".swiftpm", "DerivedData", "__pycache__", ".git", "xcuserdata", ".validation"}
PRIVATE_SUFFIXES = {".p8", ".p12", ".key", ".mobileprovision", ".jks", ".keystore", ".ipa"}

def package():
    validate_project.validate()
    DELIVERY.mkdir(parents=True, exist_ok=True)
    archive = DELIVERY / "NaviSarthi-iOS-source.zip"
    staged = archive.with_suffix(".zip.tmp")
    paths = [p for p in sorted(ROOT.rglob("*")) if p.is_file()
             and not any(part in SKIP for part in p.relative_to(ROOT).parts)
             and p.suffix.lower() not in PRIVATE_SUFFIXES
             and not p.name.lower().startswith((".env", "serviceaccount", "service-account"))]
    with zipfile.ZipFile(staged, "w", zipfile.ZIP_DEFLATED,strict_timestamps=False) as output:
        for path in paths:
            output.write(path, (Path("iOS") / path.relative_to(ROOT)).as_posix())
    with zipfile.ZipFile(staged) as check:
        assert check.testzip() is None
        assert "iOS/NaviSarthi.xcodeproj/project.pbxproj" in check.namelist()
        assert "iOS/Package.swift" in check.namelist()
        assert len(check.namelist()) == len(paths)
    staged.replace(archive)
    checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
    (DELIVERY / (archive.name + ".sha256")).write_text(f"{checksum}  {archive.name}\n", encoding="utf-8")
    (DELIVERY / "DELIVERY.md").write_text(
        "# NaviSarthi iOS source delivery\n\n"
        "Expanded native iOS implementation prepared on Windows on 2026-11-19.\n"
        "Extract the ZIP, open iOS/NaviSarthi.xcodeproj on a Mac, and follow iOS/README.md.\n\n"
        "Source includes native regional offline road routing/rendering, traffic and GPX,\n"
        "Garage/rides, Android backup import and automatic collection sync with conflicts,\n"
        "Google OAuth/Drive, Share Extension, Comms/conditional audio and Dash/Pod adapters.\n"
        "Public Music/call-state controls and experimental iOS 26 projection are included.\n"
        "SDKs, extension embedding, OAuth/APNs and branding helpers require receiving-Mac setup.\n"
        "This is not complete Android parity. See iOS/docs/PARITY.md.\n\n"
        "See VERIFICATION.json for grammar checks. Structural checks and ZIP integrity passed. Compilation, tests, signing,\n"
        "simulator and hardware validation were not run. No IPA has been produced.\n",
        encoding="utf-8")
    (DELIVERY / "VERIFICATION.json").write_text(json.dumps({
        "artifact":archive.name, "sha256":checksum, "files":len(paths), "zipIntegrity":"passed",
        "projectChecks":"passed", "compiled":False, "signed":False,
        "swiftSyntax":json.loads((ROOT / "VERIFICATION.json").read_text(encoding="utf-8"))["swiftSyntax"], "audioSDKLinked":False,
        "shareTargetConfigured":False, "pushSDKLinked":False,
        "swiftTestsExecuted":False, "hardwareValidated":False,
        "featureParity":"partial; see iOS/docs/PARITY.md"
    }, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"archive":str(archive), "files":len(paths), "sha256":checksum}, indent=2))

if __name__ == "__main__": package()
