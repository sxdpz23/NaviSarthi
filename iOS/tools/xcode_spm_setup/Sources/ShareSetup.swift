import Foundation
import XcodeProj
import PathKit
import AppKit

func installShareExtension(projectPath:Path,groupID:String) throws {
    guard groupID.hasPrefix("group."), groupID.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || $0 == 46 || $0 == 45 }) else {
        throw NSError(domain:"ShareSetup",code:1,userInfo:[NSLocalizedDescriptionKey:"Supply your registered App Group ID"])
    }
    let project = try XcodeProj(path:projectPath), pbx = project.pbxproj
    guard let root = try pbx.rootProject(), let app = pbx.nativeTargets.first(where:{ $0.productType == .application }) else { throw NSError(domain:"ShareSetup",code:2) }
    let sourceRoot = projectPath.parent()
    let appBundle = app.buildConfigurationList?.buildConfigurations.first?.buildSettings["PRODUCT_BUNDLE_IDENTIFIER"] as? String ?? "com.navisarthi.ios"
    let name = "NaviSarthiShare"
    if !pbx.nativeTargets.contains(where:{ $0.name == name }) {
        let group = PBXGroup(children:[],sourceTree:.group,name:"ShareExtension",path:"ShareExtension")
        pbx.add(object:group); root.mainGroup.children.append(group)
        let source = PBXFileReference(sourceTree:.group,lastKnownFileType:"sourcecode.swift",path:"ShareViewController.swift")
        pbx.add(object:source); group.children.append(source)
        let sourceBuild = PBXBuildFile(file:source); pbx.add(object:sourceBuild)
        let sources = PBXSourcesBuildPhase(files:[sourceBuild]), resources = PBXResourcesBuildPhase(), frameworks = PBXFrameworksBuildPhase()
        pbx.add(object:sources); pbx.add(object:resources); pbx.add(object:frameworks)
        var configurations:[XCBuildConfiguration] = []
        for mode in ["Debug","Release"] {
            let settings:[String:Any] = [
                "PRODUCT_NAME":name,"PRODUCT_BUNDLE_IDENTIFIER":appBundle + ".share",
                "INFOPLIST_FILE":"ShareExtension/Info.plist","GENERATE_INFOPLIST_FILE":"NO",
                "CODE_SIGN_ENTITLEMENTS":"ShareExtension/Share.entitlements","CODE_SIGN_STYLE":"Automatic",
                "NAVISARTHI_APP_GROUP":groupID,"SKIP_INSTALL":"YES","APPLICATION_EXTENSION_API_ONLY":"YES",
                "SWIFT_VERSION":"5.0","IPHONEOS_DEPLOYMENT_TARGET":"17.0","TARGETED_DEVICE_FAMILY":"1,2",
                "MARKETING_VERSION":"0.7.1","CURRENT_PROJECT_VERSION":"1",
                "LD_RUNPATH_SEARCH_PATHS":"$(inherited) @executable_path/Frameworks @executable_path/../../Frameworks"
            ]
            let config = XCBuildConfiguration(name:mode,buildSettings:settings); pbx.add(object:config); configurations.append(config)
        }
        let configList = XCConfigurationList(buildConfigurations:configurations,defaultConfigurationName:"Release")
        pbx.add(object:configList)
        let product = PBXFileReference(sourceTree:.buildProductsDir,explicitFileType:"wrapper.app-extension",path:name + ".appex",includeInIndex:false)
        pbx.add(object:product); root.productsGroup?.children.append(product)
        let target = PBXNativeTarget(name:name,buildConfigurationList:configList,buildPhases:[sources,frameworks,resources],productName:name,product:product,productType:.appExtension)
        pbx.add(object:target); root.targets.append(target)
        let dependency = PBXTargetDependency(target:target); pbx.add(object:dependency); app.dependencies.append(dependency)
        let embedFile = PBXBuildFile(file:product,settings:["ATTRIBUTES":["RemoveHeadersOnCopy","CodeSignOnCopy"]]); pbx.add(object:embedFile)
        let embed = PBXCopyFilesBuildPhase(dstSubfolderSpec:.plugins,name:"Embed App Extensions",files:[embedFile]); pbx.add(object:embed); app.buildPhases.append(embed)
    }
    for target in pbx.nativeTargets where target.name == name || target == app {
        for configuration in target.buildConfigurationList?.buildConfigurations ?? [] { configuration.buildSettings["NAVISARTHI_APP_GROUP"] = groupID }
    }
    func updatePlist(_ relative:String,change:(inout [String:Any])->Void) throws {
        let url = URL(fileURLWithPath:(sourceRoot + Path(relative)).string)
        var object = try PropertyListSerialization.propertyList(from:Data(contentsOf:url),format:nil) as? [String:Any] ?? [:]
        change(&object)
        try PropertyListSerialization.data(fromPropertyList:object,format:.xml,options:0).write(to:url,options:.atomic)
    }
    try updatePlist("Configuration/Info.plist") { $0["NaviSarthiAppGroup"] = groupID }
    try updatePlist("Configuration/NaviSarthi.entitlements") { object in
        var groups = object["com.apple.security.application-groups"] as? [String] ?? []
        if !groups.contains(groupID) { groups.append(groupID) }; object["com.apple.security.application-groups"] = groups
    }
    try updatePlist("ShareExtension/Share.entitlements") { $0["com.apple.security.application-groups"] = [groupID] }
    try project.write(path:projectPath)
    print("Share Extension configured. Register and enable \(groupID) for BOTH bundle IDs, then compile and test in Xcode.")
}

func configureGoogleOAuth(projectPath:Path,clientID:String) throws {
    guard clientID.hasSuffix(".apps.googleusercontent.com"), clientID.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || $0 == 45 || $0 == 46 }) else { throw NSError(domain:"GoogleSetup",code:1,userInfo:[NSLocalizedDescriptionKey:"Supply your public iOS OAuth client ID"] ) }
    let url = URL(fileURLWithPath:(projectPath.parent() + Path("Configuration/Info.plist")).string)
    var info = try PropertyListSerialization.propertyList(from:Data(contentsOf:url),format:nil) as? [String:Any] ?? [:]
    let scheme = clientID.split(separator:".").reversed().joined(separator:".")
    var types = info["CFBundleURLTypes"] as? [[String:Any]] ?? []
    if !types.contains(where:{ ($0["CFBundleURLSchemes"] as? [String] ?? []).contains(scheme) }) {
        types.append(["CFBundleURLName":"Google OAuth","CFBundleURLSchemes":[scheme]])
    }
    info["CFBundleURLTypes"] = types; info["NaviSarthiGoogleClientID"] = clientID
    try PropertyListSerialization.data(fromPropertyList:info,format:.xml,options:0).write(to:url,options:.atomic)
    print("Google OAuth callback configured. Verify the client belongs to this bundle ID and Firebase project; enable Drive API and Google sign-in there.")
}

func configureBranding(projectPath:Path) throws {
    let base = projectPath.parent(), source = URL(fileURLWithPath:(base + Path("Branding/original.png")).string)
    guard let image = NSImage(contentsOf:source), let bitmap = NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:1024,pixelsHigh:1024,bitsPerSample:8,samplesPerPixel:3,hasAlpha:false,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0), let context = NSGraphicsContext(bitmapImageRep:bitmap) else { throw NSError(domain:"Branding",code:1) }
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = context
    NSColor.black.setFill(); NSBezierPath(rect:NSRect(x:0,y:0,width:1024,height:1024)).fill()
    image.draw(in:NSRect(x:0,y:0,width:1024,height:1024),from:.zero,operation:.sourceOver,fraction:1)
    NSGraphicsContext.restoreGraphicsState()
    let assets = URL(fileURLWithPath:(base + Path("Assets.xcassets")).string), icon = assets.appendingPathComponent("AppIcon.appiconset",isDirectory:true)
    try FileManager.default.createDirectory(at:icon,withIntermediateDirectories:true)
    guard let png = bitmap.representation(using:.png,properties:[:]) else { throw NSError(domain:"Branding",code:2) }
    try png.write(to:icon.appendingPathComponent("AppIcon.png"),options:.atomic)
    let contents:[String:Any] = ["images":[["filename":"AppIcon.png","idiom":"universal","platform":"ios","size":"1024x1024"]],"info":["author":"xcode","version":1]]
    try JSONSerialization.data(withJSONObject:contents,options:.prettyPrinted).write(to:icon.appendingPathComponent("Contents.json"),options:.atomic)
    try JSONSerialization.data(withJSONObject:["info":["author":"xcode","version":1]]).write(to:assets.appendingPathComponent("Contents.json"),options:.atomic)
    let project = try XcodeProj(path:projectPath), pbx = project.pbxproj
    guard let root = try pbx.rootProject(), let app = pbx.nativeTargets.first(where:{ $0.productType == .application }), let resources = app.buildPhases.compactMap({ $0 as? PBXResourcesBuildPhase }).first else { throw NSError(domain:"Branding",code:3) }
    if !pbx.fileReferences.contains(where:{ $0.path == "Assets.xcassets" }) {
        let file = PBXFileReference(sourceTree:.sourceRoot,lastKnownFileType:"folder.assetcatalog",path:"Assets.xcassets")
        pbx.add(object:file); root.mainGroup.children.append(file); _ = try resources.add(file:file)
    }
    for configuration in app.buildConfigurationList?.buildConfigurations ?? [] { configuration.buildSettings["ASSETCATALOG_COMPILER_APPICON_NAME"] = "AppIcon" }
    try project.write(path:projectPath)
    print("Existing NaviSarthi artwork prepared as a 1024px opaque iOS app icon and linked.")
}

func configurePush(projectPath:Path) throws {
    let project = try XcodeProj(path:projectPath), pbx = project.pbxproj
    guard let root = try pbx.rootProject(), let app = pbx.nativeTargets.first(where:{ $0.productType == .application }) else { throw NSError(domain:"PushSetup",code:1) }
    let url = URL(fileURLWithPath:(projectPath.parent() + Path("Configuration/NaviSarthi.entitlements")).string)
    var entitlements = try PropertyListSerialization.propertyList(from:Data(contentsOf:url),format:nil) as? [String:Any] ?? [:]
    entitlements["aps-environment"] = "development"
    try PropertyListSerialization.data(fromPropertyList:entitlements,format:.xml,options:0).write(to:url,options:.atomic)
    var attributes = root.targetAttributes, target = attributes[app] ?? [:], capabilities = target["SystemCapabilities"] as? [String:Any] ?? [:]
    capabilities["com.apple.Push"] = ["enabled":1]; target["SystemCapabilities"] = capabilities; attributes[app] = target; root.targetAttributes = attributes
    try project.write(path:projectPath)
    print("Push capability declared. Register the bundle ID and upload your APNs key in Firebase; never put the private APNs key in this source tree.")
}
