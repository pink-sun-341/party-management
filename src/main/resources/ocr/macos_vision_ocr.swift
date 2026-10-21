import Foundation
import Vision
import ImageIO
import CoreGraphics

guard CommandLine.arguments.count > 1 else { exit(2) }
let imageURL = URL(fileURLWithPath: CommandLine.arguments[1])
guard let source = CGImageSourceCreateWithURL(imageURL as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { exit(3) }

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = true
request.recognitionLanguages = ["zh-Hans", "en-US"]
do {
    try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
} catch {
    fputs("OCR error: \(error)\n", stderr)
    exit(4)
}

let observations = request.results ?? []
guard CommandLine.arguments.count > 2 else {
    for observation in observations {
        if let text = observation.topCandidates(1).first?.string { print(text) }
    }
    exit(0)
}

let memberFile = URL(fileURLWithPath: CommandLine.arguments[2])
guard let memberText = try? String(contentsOf: memberFile, encoding: .utf8) else { exit(5) }
let members = memberText.split(whereSeparator: \.isNewline).map(String.init).filter { !$0.isEmpty }

struct OCRItem {
    let text: String
    let box: CGRect
}

let items = observations.compactMap { observation -> OCRItem? in
    guard let text = observation.topCandidates(1).first?.string else { return nil }
    return OCRItem(text: text, box: observation.boundingBox)
}

func compact(_ value: String) -> String {
    value.replacingOccurrences(of: "\\s", with: "", options: .regularExpression)
        .replacingOccurrences(of: "，", with: ",")
        .trimmingCharacters(in: .whitespacesAndNewlines)
}

func escaped(_ value: String) -> String {
    value.replacingOccurrences(of: "\\t", with: " ")
        .replacingOccurrences(of: "\\r", with: " ")
        .replacingOccurrences(of: "\\n", with: " ")
}

func overlap(_ first: CGRect, _ second: CGRect) -> Bool {
    first.intersects(second) || first.contains(CGPoint(x: second.midX, y: second.midY)) || second.contains(CGPoint(x: first.midX, y: first.midY))
}

func isSlashOnly(_ value: String) -> Bool {
    let compacted = compact(value)
    return !compacted.isEmpty && compacted.allSatisfy { "/／\\—-_".contains($0) }
}

// Vision coordinates start at the lower-left. Keep the image sampling in the
// same coordinate system so a name can be paired with its adjacent sign cell.
var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
pixels.withUnsafeMutableBytes { rawBuffer in
    let pixelBuffer = rawBuffer.bindMemory(to: UInt8.self)
    guard let context = CGContext(data: rawBuffer.baseAddress,
                                  width: image.width,
                                  height: image.height,
                                  bitsPerComponent: 8,
                                  bytesPerRow: image.width * 4,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { exit(6) }
    context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))

    struct InkMetrics {
        let density: Double
        let rowCoverage: Double
        let columnCoverage: Double
    }

    func inkMetrics(_ normalized: CGRect) -> InkMetrics {
        // Ignore the printed cell border and sample only the writing area.
        // A border can still be dark after cropping, so keep its horizontal
        // and vertical coverage to reject straight table lines as ink.
        let insetX = normalized.width * 0.28
        let insetY = normalized.height * 0.40
        let area = normalized.insetBy(dx: insetX, dy: insetY)
        let x1 = max(0, Int(area.minX * CGFloat(image.width)))
        let x2 = min(image.width - 1, Int(area.maxX * CGFloat(image.width)))
        let y1 = max(0, Int(area.minY * CGFloat(image.height)))
        let y2 = min(image.height - 1, Int(area.maxY * CGFloat(image.height)))
        if x2 <= x1 || y2 <= y1 { return InkMetrics(density: 0, rowCoverage: 0, columnCoverage: 0) }
        var dark = 0
        var total = 0
        var darkRows = Set<Int>()
        var darkColumns = Set<Int>()
        let sampleStride = max(1, min(image.width, image.height) / 900)
        for y in Swift.stride(from: y1, through: y2, by: sampleStride) {
            for x in Swift.stride(from: x1, through: x2, by: sampleStride) {
                let index = (y * image.width + x) * 4
                let red = Int(pixelBuffer[index])
                let green = Int(pixelBuffer[index + 1])
                let blue = Int(pixelBuffer[index + 2])
                let luminance = (299 * red + 587 * green + 114 * blue) / 1000
                if luminance < 145 {
                    dark += 1
                    darkRows.insert(y)
                    darkColumns.insert(x)
                }
                total += 1
            }
        }
        let rowCount = max(1, ((y2 - y1) / sampleStride) + 1)
        let columnCount = max(1, ((x2 - x1) / sampleStride) + 1)
        return InkMetrics(
            density: total == 0 ? 0 : Double(dark) / Double(total),
            rowCoverage: Double(darkRows.count) / Double(rowCount),
            columnCoverage: Double(darkColumns.count) / Double(columnCount)
        )
    }

    for member in members {
        let target = compact(member)
        let candidates = items.filter { compact($0.text).contains(target) }
        guard let item = candidates.max(by: { compact($0.text).count < compact($1.text).count }) else {
            print("RESULT\t\(escaped(member))\tfalse\tfalse\t0\t")
            continue
        }

        let box = item.box
        let centerX = box.midX
        let leftGroup = centerX < 0.5
        let signMinX = leftGroup ? max(box.maxX + 0.015, 0.25) : max(box.maxX + 0.015, 0.66)
        let signMaxX = leftGroup ? min(signMinX + 0.19, 0.50) : min(signMinX + 0.19, 0.98)
        let rowMinY = max(0, box.midY - 0.011)
        let rowMaxY = min(1, box.midY + 0.011)
        let signBox = CGRect(x: signMinX, y: rowMinY, width: max(0.01, signMaxX - signMinX), height: rowMaxY - rowMinY)
        let metrics = inkMetrics(signBox)
        let score = metrics.density
        let signItems = items.filter { overlap($0.box, signBox) }
        let evidence = signItems.map { compact($0.text) }.joined(separator: ",")
        let slashOnly = isSlashOnly(evidence)
        let explicitAttendance = evidence.contains("线上") || evidence.contains("在线") || evidence.contains("参加") || evidence.contains("出席")
        let hasHandwriting = score >= 0.055 && metrics.rowCoverage >= 0.14 && metrics.columnCoverage >= 0.10
        let present = (explicitAttendance || hasHandwriting) && !slashOnly
        print(String(format: "RESULT\t%@\ttrue\t%@\t%.4f\t%@", escaped(member), present ? "true" : "false", score, escaped(evidence)))
    }
}
