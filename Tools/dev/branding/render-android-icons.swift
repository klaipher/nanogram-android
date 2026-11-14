// Renders Nanogram's Android launcher icons, the caller icon and the intro texture from the shared Nanogram artwork
// (the iOS/Mac app icon layers and the alternate-icon SVGs). Output goes to TMessagesProj_App/src/main/res, where
// it overrides upstream's Telegram artwork without touching upstream files. Re-run after changing a source:
//   swift Tools/dev/branding/render-android-icons.swift
// SVGs are drawn with AppKit's native SVG support (keeps transparency, needed for the adaptive foreground layer).
import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
    .deletingLastPathComponent().deletingLastPathComponent()
let res = repo.appendingPathComponent("TMessagesProj_App/src/main/res")
let ios = URL(fileURLWithPath: ProcessInfo.processInfo.environment["NANOGRAM_IOS_REPO"]
    ?? NSHomeDirectory() + "/Projects/nanogram-ios").appendingPathComponent("Telegram/Telegram-iOS")
// The default icon (Sphere) as layers, the same files as the iOS/Mac Liquid Glass icon, bottom to top.
let layers = ios.appendingPathComponent("Nanogram.icon/Assets")
let backgroundLayers = ["Background.svg"]
let foregroundLayers = ["Light.svg", "LightCore.png", "Sphere.svg", "SphereSheen.png", "Sparkle.svg"]
let monochromeLayers = ["LightMono.svg", "SphereMono.svg", "SparkleMono.svg"]
// Alternate icons: upstream alias resource suffix -> Nanogram design (full-bleed SVG), as on iOS.
let alternates = ios.appendingPathComponent("AlternateIconSources")
let alternateIcons: [(resource: String, svg: String)] = [
    ("_2", "NanogramNight"),
    ("_3", "NanogramGlass"),
    ("_4", "NanogramOrbit"),
    ("_5", "NanogramSpeed"),
    ("_6", "NanogramComet"),
]
let densities: [(name: String, scale: CGFloat)] = [
    ("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4),
]
// Adaptive icons are 108 dp. The Sphere artwork is drawn at 80 dp so the beam and sphere stay inside round masks;
// the starry background covers the whole layer. Flat alternates use the 66 dp safe zone.
let foregroundDp: CGFloat = 80
let alternateDp: CGFloat = 66

func fail(_ message: String) -> Never {
    FileHandle.standardError.write((message + "\n").data(using: .utf8)!)
    exit(1)
}

func image(_ url: URL) -> NSImage {
    guard FileManager.default.fileExists(atPath: url.path), let img = NSImage(contentsOf: url) else { fail("cannot load \(url.path)") }
    return img
}

func context(_ width: Int, _ height: Int) -> CGContext {
    guard let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                              space: CGColorSpace(name: CGColorSpace.sRGB)!,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { fail("context") }
    ctx.interpolationQuality = .high
    return ctx
}

func draw(_ files: [URL], in ctx: CGContext, rect: CGRect) {
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    for f in files {
        image(f).draw(in: rect)
    }
    NSGraphicsContext.restoreGraphicsState()
}

func write(_ ctx: CGContext, to url: URL) {
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard let img = ctx.makeImage(),
          let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fail("write \(url.path)")
    }
    CGImageDestinationAddImage(dest, img, nil)
    if !CGImageDestinationFinalize(dest) { fail("finalize \(url.path)") }
}

func centered(_ px: Int, dp: CGFloat) -> CGRect {
    let side = CGFloat(px) * dp / 108
    let origin = (CGFloat(px) - side) / 2
    return CGRect(x: origin, y: origin, width: side, height: side)
}

/// Colour of a flat design's corner, used to extend it into the adaptive icon's bleed area.
func cornerColor(_ files: [URL]) -> CGColor {
    let ctx = context(64, 64)
    draw(files, in: ctx, rect: CGRect(x: 0, y: 0, width: 64, height: 64))
    let p = ctx.data!.assumingMemoryBound(to: UInt8.self)
    let i = (2 * 64 + 2) * 4
    return CGColor(srgbRed: CGFloat(p[i]) / 255, green: CGFloat(p[i + 1]) / 255, blue: CGFloat(p[i + 2]) / 255, alpha: 1)
}

/// Legacy (API 23-25) launcher icon and round variants: the whole design in a rounded square or a circle.
func legacy(_ files: [URL], px: Int, round: Bool, margin: CGFloat = 2) -> CGContext {
    let ctx = context(px, px)
    let m = CGFloat(px) * margin / 48
    let rect = CGRect(x: m, y: m, width: CGFloat(px) - 2 * m, height: CGFloat(px) - 2 * m)
    ctx.addPath(round ? CGPath(ellipseIn: rect, transform: nil)
        : CGPath(roundedRect: rect, cornerWidth: rect.width * 0.18, cornerHeight: rect.width * 0.18, transform: nil))
    ctx.clip()
    draw(files, in: ctx, rect: rect)
    return ctx
}

let background = backgroundLayers.map { layers.appendingPathComponent($0) }
let foreground = foregroundLayers.map { layers.appendingPathComponent($0) }
let monochrome = monochromeLayers.map { layers.appendingPathComponent($0) }
let sphere = background + foreground

for d in densities {
    let dir = res.appendingPathComponent("mipmap-\(d.name)")
    let px = Int(108 * d.scale)
    var ctx = context(px, px)
    draw(background, in: ctx, rect: CGRect(x: 0, y: 0, width: px, height: px))
    write(ctx, to: dir.appendingPathComponent("nanogram_icon_background.png"))
    ctx = context(px, px)
    draw(foreground, in: ctx, rect: centered(px, dp: foregroundDp))
    write(ctx, to: dir.appendingPathComponent("nanogram_icon_foreground.png"))
    ctx = context(px, px)
    draw(monochrome, in: ctx, rect: centered(px, dp: foregroundDp))
    write(ctx, to: dir.appendingPathComponent("nanogram_icon_monochrome.png"))
    ctx = context(px, px)
    draw(background, in: ctx, rect: CGRect(x: 0, y: 0, width: px, height: px))
    draw(foreground, in: ctx, rect: centered(px, dp: foregroundDp))
    write(ctx, to: dir.appendingPathComponent("nanogram_icon_full.png"))
    write(legacy(sphere, px: Int(48 * d.scale), round: false), to: dir.appendingPathComponent("ic_launcher.png"))
    write(legacy(sphere, px: Int(48 * d.scale), round: true), to: dir.appendingPathComponent("ic_launcher_round.png"))
    write(legacy(sphere, px: Int(48 * d.scale), round: true, margin: 0),
          to: res.appendingPathComponent("drawable-\(d.name)/ic_launcher_dr.png"))
}
print("rendered Sphere (default, layered)")

for icon in alternateIcons {
    let files = [alternates.appendingPathComponent(icon.svg + ".svg")]
    let fill = cornerColor(files)
    for d in densities {
        let dir = res.appendingPathComponent("mipmap-\(d.name)")
        let px = Int(108 * d.scale)
        let ctx = context(px, px)
        ctx.setFillColor(fill)
        ctx.fill(CGRect(x: 0, y: 0, width: px, height: px))
        draw(files, in: ctx, rect: centered(px, dp: alternateDp))
        write(ctx, to: dir.appendingPathComponent("nanogram_icon\(icon.resource)_full.png"))
        write(legacy(files, px: Int(48 * d.scale), round: false), to: dir.appendingPathComponent("icon\(icon.resource)_launcher.png"))
        write(legacy(files, px: Int(48 * d.scale), round: true), to: dir.appendingPathComponent("icon\(icon.resource)_launcher_round.png"))
    }
    print("rendered \(icon.svg) -> icon\(icon.resource)")
}

// Intro texture (drawn by IntroActivity on a dark disc): beam, sphere and sparkle without the square glow, framed to
// the artwork's bounds (x 18-76, y 22-86 of 100), same pixel size as upstream's intro_tg_plane (82 x 74 dp).
let lightSource = try! String(contentsOf: layers.appendingPathComponent("Light.svg"), encoding: .utf8)
let beamOnly = FileManager.default.temporaryDirectory.appendingPathComponent("nanogram-light-beam.svg")
try! lightSource.replacingOccurrences(of: "<rect width=\"100\" height=\"100\" fill=\"url(#kGlow)\"/>", with: "")
    .write(to: beamOnly, atomically: true, encoding: .utf8)
let introLayers = [beamOnly] + ["Sphere.svg", "SphereSheen.png", "Sparkle.svg"].map { layers.appendingPathComponent($0) }
for d in densities where d.name != "xxxhdpi" {
    let w = Int(82 * d.scale), h = Int(74 * d.scale)
    let ctx = context(w, h)
    let side = CGFloat(h) / 0.66
    let originX = CGFloat(w) / 2 - side * 0.47
    let originY = CGFloat(h) / 2 - side * (1 - 0.54)
    draw(introLayers, in: ctx, rect: CGRect(x: originX, y: originY, width: side, height: side))
    write(ctx, to: res.appendingPathComponent("drawable-\(d.name)/intro_tg_plane.png"))
}
print("rendered intro Sphere")
