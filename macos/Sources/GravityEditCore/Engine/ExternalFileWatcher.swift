import Foundation

public struct FileDiskMetadata: Equatable, Sendable {
    public let modificationTime: TimeInterval
    public let byteSize: Int64

    public init(modificationTime: TimeInterval, byteSize: Int64) {
        self.modificationTime = modificationTime
        self.byteSize = byteSize
    }
}

public enum ExternalFileChangeEvent: Equatable, Sendable {
    case unchanged
    case modifiedExternally(newMetadata: FileDiskMetadata)
    case deletedOnDisk
}

public enum ExternalFileWatcher {

    public static func inspectDiskMetadata(at url: URL) -> FileDiskMetadata? {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path) else {
            return nil
        }
        let modDate = (attrs[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
        return FileDiskMetadata(modificationTime: modDate, byteSize: size)
    }

    public static func checkExternalChange(
        at url: URL,
        lastKnownModDate: TimeInterval?
    ) -> ExternalFileChangeEvent {
        guard let current = inspectDiskMetadata(at: url) else {
            return .deletedOnDisk
        }
        guard let previous = lastKnownModDate else {
            return .unchanged
        }
        if abs(current.modificationTime - previous) > 0.001 {
            return .modifiedExternally(newMetadata: current)
        }
        return .unchanged
    }

    /// Reads the latest text from disk for Live Tail or external reload, capping huge log tails cleanly if requested.
    public static func readLatestDiskText(
        at url: URL,
        maxTailBytes: Int? = nil
    ) -> (text: String, encoding: DocumentEncoding, lineEnding: LineEnding, metadata: FileDiskMetadata)? {
        guard let meta = inspectDiskMetadata(at: url) else { return nil }
        let data: Data
        if let maxBytes = maxTailBytes, meta.byteSize > Int64(maxBytes),
           let handle = try? FileHandle(forReadingFrom: url) {
            defer { try? handle.close() }
            let startOffset = UInt64(max(0, meta.byteSize - Int64(maxBytes)))
            try? handle.seek(toOffset: startOffset)
            data = (try? handle.readToEnd()) ?? Data()
        } else {
            guard let fullData = try? Data(contentsOf: url) else { return nil }
            data = fullData
        }
        let (rawText, encoding) = DocumentEncoding.decode(data)
        let le = LineEnding.detect(rawText)
        let normalized = LineEnding.normalizeToLf(rawText)
        return (normalized, encoding, le, meta)
    }
}
