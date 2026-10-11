import Foundation
import Vision
import ImageIO
import CoreGraphics
import Darwin

guard CommandLine.arguments.count > 1 else { exit(2) }
let imageURL = URL(fileURLWithPath: CommandLine.arguments[1])
guard let source = CGImageSourceCreateWithURL(imageURL as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { exit(3) }

let request = VNRecognizeTextRequest { request, error in
    if let error = error { fputs("OCR error: \(error)\n", stderr); exit(4) }
    let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
    for observation in observations {
        if let text = observation.topCandidates(1).first?.string {
            print(text)
        }
    }
}
request.recognitionLevel = .accurate
request.usesLanguageCorrection = true
request.recognitionLanguages = ["zh-Hans", "en-US"]
try? VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
