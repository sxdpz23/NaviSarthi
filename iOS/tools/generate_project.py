"""Deterministic Xcode project generator. Python stdlib only; runs on Windows/macOS."""
from pathlib import Path
import hashlib
import json
import plistlib

ROOT = Path(__file__).resolve().parents[1]
OBJECTS = {}

def identifier(label):
    return hashlib.sha256(label.encode()).hexdigest()[:24].upper()

def add(label, isa, **attributes):
    key = identifier(label)
    OBJECTS[key] = dict(isa=isa, **attributes)
    return key

def serialize(value, depth=0):
    if isinstance(value, dict):
        return "{\n" + "".join("\t" * (depth + 1) + json.dumps(str(k)) + " = " + serialize(v, depth + 1) + ";\n" for k, v in value.items()) + "\t" * depth + "}"
    if isinstance(value, list):
        return "(\n" + "".join("\t" * (depth + 1) + serialize(v, depth + 1) + ",\n" for v in value) + "\t" * depth + ")"
    return json.dumps(str(value))

def generate(write=True):
    OBJECTS.clear()
    source_refs, source_builds = [], []
    for path in sorted((ROOT / "Sources/App").glob("*.swift")):
        relative = path.relative_to(ROOT).as_posix()
        ref = add(relative, "PBXFileReference", lastKnownFileType="sourcecode.swift", path=relative, sourceTree="SOURCE_ROOT")
        source_refs.append(ref)
        source_builds.append(add(relative + ":build", "PBXBuildFile", fileRef=ref))
    config_refs = [add("config:" + name, "PBXFileReference", lastKnownFileType="text.plist.xml", path="Configuration/" + name, sourceTree="SOURCE_ROOT") for name in ["Info.plist", "NaviSarthi.entitlements", "PrivacyInfo.xcprivacy"]]
    privacy_build = add("privacy:build", "PBXBuildFile", fileRef=config_refs[2])
    product = add("product", "PBXFileReference", explicitFileType="wrapper.application", includeInIndex=0, path="NaviSarthi.app", sourceTree="BUILT_PRODUCTS_DIR")
    sources = add("sources", "PBXSourcesBuildPhase", buildActionMask=2147483647, files=source_builds, runOnlyForDeploymentPostprocessing=0)
    package = add("package", "XCLocalSwiftPackageReference", relativePath=".")
    dependency = add("package:product", "XCSwiftPackageProductDependency", package=package, productName="NaviSarthiCore")
    framework = add("package:build", "PBXBuildFile", productRef=dependency)
    frameworks = add("frameworks", "PBXFrameworksBuildPhase", buildActionMask=2147483647, files=[framework], runOnlyForDeploymentPostprocessing=0)
    resources = add("resources", "PBXResourcesBuildPhase", buildActionMask=2147483647, files=[privacy_build], runOnlyForDeploymentPostprocessing=0)
    configs, target_configs = [], []
    for name in ["Debug", "Release"]:
        settings = dict(SDKROOT="iphoneos", IPHONEOS_DEPLOYMENT_TARGET="17.0", SWIFT_VERSION="5.0", CLANG_ENABLE_MODULES="YES", CLANG_ENABLE_OBJC_ARC="YES", SWIFT_OPTIMIZATION_LEVEL="-Onone" if name == "Debug" else "-O", DEBUG_INFORMATION_FORMAT="dwarf" if name == "Debug" else "dwarf-with-dsym", ENABLE_TESTABILITY="YES" if name == "Debug" else "NO")
        if name == "Debug": settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "DEBUG $(inherited)"
        configs.append(add("project:" + name, "XCBuildConfiguration", name=name, buildSettings=settings))
        target_settings = dict(PRODUCT_NAME="NaviSarthi", PRODUCT_BUNDLE_IDENTIFIER="com.navisarthi.ios", MARKETING_VERSION="0.7.1", CURRENT_PROJECT_VERSION="1", INFOPLIST_FILE="Configuration/Info.plist", GENERATE_INFOPLIST_FILE="NO", CODE_SIGN_ENTITLEMENTS="Configuration/NaviSarthi.entitlements", CODE_SIGN_STYLE="Automatic", DEVELOPMENT_TEAM="", TARGETED_DEVICE_FAMILY="1,2", SUPPORTED_PLATFORMS="iphoneos iphonesimulator", SUPPORTS_MACCATALYST="NO", SWIFT_EMIT_LOC_STRINGS="YES", LD_RUNPATH_SEARCH_PATHS="$(inherited) @executable_path/Frameworks")
        target_configs.append(add("target:" + name, "XCBuildConfiguration", name=name, buildSettings=target_settings))
    project_config = add("project:configs", "XCConfigurationList", buildConfigurations=configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")
    target_config = add("target:configs", "XCConfigurationList", buildConfigurations=target_configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")
    target = add("target", "PBXNativeTarget", buildConfigurationList=target_config, buildPhases=[sources, frameworks, resources], buildRules=[], dependencies=[], name="NaviSarthi", packageProductDependencies=[dependency], productName="NaviSarthi", productReference=product, productType="com.apple.product-type.application")
    source_group = add("source:group", "PBXGroup", name="App", children=source_refs, sourceTree="<group>")
    config_group = add("config:group", "PBXGroup", name="Configuration", children=config_refs, sourceTree="<group>")
    products = add("products", "PBXGroup", name="Products", children=[product], sourceTree="<group>")
    main_group = add("main", "PBXGroup", children=[source_group, config_group, products], sourceTree="<group>")
    project = add("project", "PBXProject", attributes={"LastUpgradeCheck":"1600", "TargetAttributes":{target:{"CreatedOnToolsVersion":"16.0", "SystemCapabilities":{"com.apple.HotspotConfiguration":{"enabled":1},"com.apple.BackgroundModes":{"enabled":1}}}}}, buildConfigurationList=project_config, compatibilityVersion="Xcode 14.0", developmentRegion="en", hasScannedForEncodings=0, knownRegions=["en", "Base"], mainGroup=main_group, productRefGroup=products, projectDirPath="", projectRoot="", packageReferences=[package], targets=[target])
    document = dict(archiveVersion=1, classes={}, objectVersion=56, objects=OBJECTS, rootObject=project)
    serialized = "// !$*UTF8*$!\n" + serialize(document) + "\n"
    if not write:
        return serialized.encode("utf-8")
    folder = ROOT / "NaviSarthi.xcodeproj"
    folder.mkdir(exist_ok=True)
    (folder / "project.pbxproj").write_text(serialized, encoding="utf-8")
    schemes = folder / "xcshareddata/xcschemes"
    schemes.mkdir(parents=True, exist_ok=True)
    reference = f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="NaviSarthi.app" BlueprintName="NaviSarthi" ReferencedContainer="container:NaviSarthi.xcodeproj"/>'
    (schemes / "NaviSarthi.xcscheme").write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
 <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{reference}</BuildActionEntry></BuildActionEntries></BuildAction>
 <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables/></TestAction>
 <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference}</BuildableProductRunnable></LaunchAction>
 <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference}</BuildableProductRunnable></ProfileAction>
 <AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
''', encoding="utf-8")
    print(f"Generated {folder} with {len(source_refs)} app source files and local NaviSarthiCore package")

if __name__ == "__main__": generate()
