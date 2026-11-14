// Renders Nanogram's Android launcher icons and the intro Comet texture from the shared Nanogram SVGs
// (the same sources as the iOS alternate icons). Output goes to TMessagesProj_App/src/main/res, where it overrides
// upstream's Telegram artwork without touching upstream files. Re-run after changing a source:
//   swift Tools/dev/branding/render-android-icons.swift
// SVGs are rasterised with QuickLook (WebKit), like the iOS renderer, then composed with CoreGraphics.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
    .deletingLastPathComponent().deletingLastPathComponent()
let res = repo.appendingPathComponent("TMessagesProj_App/src/main/res")
let iosSources = URL(fileURLWithPath: NSHomeDirectory())
    .appendingPathComponent("Projects/nanogram-ios/Telegram/Telegram-iOS/AlternateIconSources")
let sources = URL(fileURLWithPath: ProcessInfo.processInfo.environment["NANOGRAM_ICON_SOURCES"] ?? iosSources.path)

// Upstream launcher alias -> Nanogram design (file in the sources folder).
let icons: [(resource: String, svg: String)] = [
    ("", "NanogramComet"),
    ("_2", "NanogramNight"),
    ("_3", "NanogramGlass"),
    ("_4", "NanogramOrbit"),
    ("_5", "NanogramSpeed"),
    ("_6", "NanogramWhite"),
]
let densities: [(name: String, scale: CGFloat)] = [
    ("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4),
]

func fail(_ message: String) -> Never {
    FileHandle.standardError.write((message + "\n").data(using: .utf8)!)
    exit(1)
}

func rasterize(_ svg: URL) -> CGImage {
    let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("ng-icons-\(getpid())")
    try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/qlmanage")
    process.arguments = ["-t", "-s", "1024", "-o", tmp.path, svg.path]
    process.standardOutput = FileHandle.nullDevice
    process.standardError = FileHandle.nullDevice
    do { try process.run() } catch { fail("qlmanage: \(error)") }
    process.waitUntilExit()
    let png = tmp.appendingPathComponent(svg.lastPathComponent + ".png")
    guard let src = CGImageSourceCreateWithURL(png as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(src, 0, nil) else { fail("cannot rasterise \(svg.path)") }
    return image
}

func context(_ width: Int, _ height: Int) -> CGContext {
    guard let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                              space: CGColorSpace(name: CGColorSpace.sRGB)!,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { fail("context") }
    ctx.interpolationQuality = .high
    return ctx
}

func write(_ ctx: CGContext, to url: URL) {
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard let image = ctx.makeImage(),
          let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fail("write \(url.path)")
    }
    CGImageDestinationAddImage(dest, image, nil)
    if !CGImageDestinationFinalize(dest) { fail("finalize \(url.path)") }
}

/// Colour of the design's corner, used to extend the background into the adaptive icon's bleed area.
func cornerColor(_ image: CGImage) -> CGColor {
    let ctx = context(1, 1)
    ctx.draw(image, in: CGRect(x: -2, y: -CGFloat(image.height) + 3, width: CGFloat(image.width), height: CGFloat(image.height)))
    let p = ctx.data!.assumingMemoryBound(to: UInt8.self)
    return CGColor(srgbRed: CGFloat(p[0]) / 255, green: CGFloat(p[1]) / 255, blue: CGFloat(p[2]) / 255, alpha: 1)
}

// Adaptive icon layer: 108 dp canvas; the design fills the central 66 dp safe zone (launcher masks crop the rest),
// the bleed is filled with the design's corner colour.
func adaptiveLayer(_ design: CGImage, px: Int) -> CGContext {
    let ctx = context(px, px)
    ctx.setFillColor(cornerColor(design))
    ctx.fill(CGRect(x: 0, y: 0, width: px, height: px))
    let inner = CGFloat(px) * 66 / 108
    let origin = (CGFloat(px) - inner) / 2
    ctx.draw(design, in: CGRect(x: origin, y: origin, width: inner, height: inner))
    return ctx
}

// Legacy (API 23-25) launcher icon: 48 dp with a 2 dp margin, rounded square or circle.
func legacyIcon(_ design: CGImage, px: Int, round: Bool) -> CGContext {
    let ctx = context(px, px)
    let margin = CGFloat(px) * 2 / 48
    let rect = CGRect(x: margin, y: margin, width: CGFloat(px) - 2 * margin, height: CGFloat(px) - 2 * margin)
    let path = round ? CGPath(ellipseIn: rect, transform: nil)
        : CGPath(roundedRect: rect, cornerWidth: rect.width * 0.18, cornerHeight: rect.width * 0.18, transform: nil)
    ctx.addPath(path)
    ctx.clip()
    ctx.draw(design, in: rect)
    return ctx
}

// The Comet without its background, for the intro sphere (drawn on the navy disc). Geometry from NanogramComet.svg.
func cometGlyph(width: Int, height: Int) -> CGContext {
    let ctx = context(width, height)
    let side = CGFloat(min(width, height))
    let s = side / 700
    ctx.translateBy(x: (CGFloat(width) - side) / 2, y: (CGFloat(height) - side) / 2)
    ctx.translateBy(x: 0, y: side)
    ctx.scaleBy(x: s, y: -s)
    ctx.translateBy(x: -150, y: -190)
    let blue = CGColor(srgbRed: 0x50 / 255.0, green: 0x8F / 255.0, blue: 1, alpha: 1)
    let tail = CGMutablePath()
    tail.move(to: CGPoint(x: 752, y: 550))
    tail.addLine(to: CGPoint(x: 176, y: 848))
    tail.addLine(to: CGPoint(x: 474, y: 272))
    tail.closeSubpath()
    ctx.saveGState()
    ctx.addPath(tail)
    ctx.clip()
    let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                              colors: [blue.copy(alpha: 0.95)!, blue.copy(alpha: 0)!] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(gradient, start: CGPoint(x: 613, y: 411), end: CGPoint(x: 176, y: 848), options: [])
    ctx.restoreGState()
    ctx.setFillColor(blue)
    ctx.fillEllipse(in: CGRect(x: 613 - 196, y: 411 - 196, width: 392, height: 392))
    return ctx
}

for icon in icons {
    let svg = sources.appendingPathComponent(icon.svg + ".svg")
    guard FileManager.default.fileExists(atPath: svg.path) else { fail("missing \(svg.path)") }
    let design = rasterize(svg)
    for d in densities {
        let dir = res.appendingPathComponent("mipmap-\(d.name)")
        write(adaptiveLayer(design, px: Int(108 * d.scale)), to: dir.appendingPathComponent("nanogram_icon\(icon.resource)_full.png"))
        let launcher = icon.resource.isEmpty ? "ic_launcher" : "icon\(icon.resource)_launcher"
        write(legacyIcon(design, px: Int(48 * d.scale), round: false), to: dir.appendingPathComponent("\(launcher).png"))
        write(legacyIcon(design, px: Int(48 * d.scale), round: true), to: dir.appendingPathComponent("\(launcher)_round.png"))
    }
    if icon.resource.isEmpty {
        // Round app icon used as the person/caller icon in notifications and calls (upstream ic_launcher_dr).
        for d in densities {
            let px = Int(48 * d.scale)
            let ctx = context(px, px)
            ctx.addPath(CGPath(ellipseIn: CGRect(x: 0, y: 0, width: px, height: px), transform: nil))
            ctx.clip()
            ctx.draw(design, in: CGRect(x: 0, y: 0, width: px, height: px))
            write(ctx, to: res.appendingPathComponent("drawable-\(d.name)/ic_launcher_dr.png"))
        }
    }
    print("rendered \(icon.svg) -> nanogram_icon\(icon.resource)")
}

// Intro sphere texture: same pixel size as upstream's intro_tg_plane per density (82 x 74 dp).
for d in densities where d.name != "xxxhdpi" {
    let ctx = cometGlyph(width: Int(82 * d.scale), height: Int(74 * d.scale))
    write(ctx, to: res.appendingPathComponent("drawable-\(d.name)/intro_tg_plane.png"))
}
print("rendered intro Comet")
