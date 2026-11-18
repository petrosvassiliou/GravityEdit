import AppKit
import GravityEditCore
import SwiftUI

// MARK: - Apple Liquid Glass Design System (macOS 26 Tahoe + macOS 15 Vibrancy Fallback)
//
// Strictly follows Apple Human Interface Guidelines for Liquid Glass:
// 1. Liquid Glass is reserved exclusively for floating navigation, toolbars, tab capsules,
//    status pills, popovers, and transient overlays — NEVER on the code content layer.
// 2. Respects Accessibility settings (`accessibilityReduceTransparency`,
//    `accessibilityReduceMotion`, `colorSchemeContrast`) automatically.
// 3. Uses `GlassEffectContainer` and `.glassEffectID` for fluid morphing transitions on macOS 26+.

public struct LiquidGlassCluster<Content: View>: View {
    private let spacing: CGFloat
    private let content: () -> Content

    public init(spacing: CGFloat = 8, @ViewBuilder content: @escaping () -> Content) {
        self.spacing = spacing
        self.content = content
    }

    @ViewBuilder
    public var body: some View {
        if #available(macOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) {
                content()
            }
        } else {
            content()
        }
    }
}

private struct LiquidGlassSurfaceModifier<S: Shape>: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorSchemeContrast) private var contrast
    let shape: S
    let tint: Color?

    @ViewBuilder
    func body(content: Content) -> some View {
        if reduceTransparency {
            content
                .background(Color(nsColor: .windowBackgroundColor), in: shape)
                .overlay(
                    shape.strokeBorder(
                        Color.primary.opacity(contrast == .increased ? 0.35 : 0.14),
                        lineWidth: 1
                    )
                )
        } else if #available(macOS 26.0, *) {
            if let tint {
                content.glassEffect(.regular.tint(tint), in: shape)
            } else {
                content.glassEffect(.regular, in: shape)
            }
        } else {
            content
                .background(.ultraThinMaterial, in: shape)
                .background(
                    Group {
                        if let tint {
                            shape.fill(tint.opacity(0.14))
                        }
                    }
                )
                .overlay(
                    shape.strokeBorder(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.28),
                                Color.primary.opacity(0.08)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.75
                    )
                )
        }
    }
}

private struct LiquidGlassMorphModifier<ID: Hashable & Sendable>: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let id: ID
    let namespace: Namespace.ID

    @ViewBuilder
    func body(content: Content) -> some View {
        if !reduceMotion, #available(macOS 26.0, *) {
            content.glassEffectID(id, in: namespace)
        } else {
            content
        }
    }
}

private struct LiquidGlassButtonModifier: ViewModifier {
    let prominent: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(macOS 26.0, *) {
            if prominent {
                content.buttonStyle(.glassProminent)
            } else {
                content.buttonStyle(.glass)
            }
        } else {
            if prominent {
                content.buttonStyle(.borderedProminent)
            } else {
                content.buttonStyle(.bordered)
            }
        }
    }
}

public extension View {
    func liquidGlassSurface<S: Shape>(in shape: S, tint: Color? = nil) -> some View {
        modifier(LiquidGlassSurfaceModifier(shape: shape, tint: tint))
    }

    func liquidGlassCapsule(tint: Color? = nil) -> some View {
        modifier(LiquidGlassSurfaceModifier(shape: Capsule(style: .continuous), tint: tint))
    }

    func liquidGlassCard(cornerRadius: CGFloat = 14, tint: Color? = nil) -> some View {
        modifier(LiquidGlassSurfaceModifier(shape: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous), tint: tint))
    }

    func liquidGlassMorphID<ID: Hashable & Sendable>(_ id: ID, in namespace: Namespace.ID) -> some View {
        modifier(LiquidGlassMorphModifier(id: id, namespace: namespace))
    }

    func liquidGlassButton(prominent: Bool = false) -> some View {
        modifier(LiquidGlassButtonModifier(prominent: prominent))
    }
}

// MARK: - Crisp Non-Glass Code Editor Palettes (Apple HIG Content Layer)

public struct EditorSyntaxColors {
    public let canvasBackground: NSColor
    public let gutterBackground: NSColor
    public let gutterSeparator: NSColor
    public let gutterLineNumber: NSColor
    public let gutterActiveLineNumber: NSColor
    public let currentLineHighlight: NSColor
    public let pageGuideHairline: NSColor
    public let plainText: NSColor
    public let keyword: NSColor
    public let cmdlet: NSColor
    public let variableNormal: NSColor
    public let variableScoped: NSColor
    public let variableAutomatic: NSColor
    public let parameter: NSColor
    public let `operator`: NSColor
    public let stringLiteral: NSColor
    public let stringInterpolation: NSColor
    public let number: NSColor
    public let typeAttribute: NSColor
    public let comment: NSColor
    public let helpTag: NSColor
    public let regionDirective: NSColor
    public let invisibles: NSColor

    public static func resolve(mode: AppearanceThemeMode, systemDark: Bool) -> EditorSyntaxColors {
        switch mode {
        case .light:
            return daylightCrisp
        case .dark:
            return tahoeMidnight
        case .oledPitchBlack:
            return pitchBlackPro
        case .system:
            return systemDark ? tahoeMidnight : daylightCrisp
        }
    }

    public static let daylightCrisp = EditorSyntaxColors(
        canvasBackground: NSColor(srgbRed: 0.992, green: 0.993, blue: 0.996, alpha: 1.0),
        gutterBackground: NSColor(srgbRed: 0.961, green: 0.965, blue: 0.974, alpha: 1.0),
        gutterSeparator: NSColor(srgbRed: 0.86, green: 0.88, blue: 0.91, alpha: 1.0),
        gutterLineNumber: NSColor(srgbRed: 0.52, green: 0.56, blue: 0.62, alpha: 1.0),
        gutterActiveLineNumber: NSColor(srgbRed: 0.12, green: 0.34, blue: 0.78, alpha: 1.0),
        currentLineHighlight: NSColor(srgbRed: 0.08, green: 0.42, blue: 0.95, alpha: 0.055),
        pageGuideHairline: NSColor(srgbRed: 0.45, green: 0.50, blue: 0.58, alpha: 0.16),
        plainText: NSColor(srgbRed: 0.11, green: 0.13, blue: 0.17, alpha: 1.0),
        keyword: NSColor(srgbRed: 0.61, green: 0.14, blue: 0.58, alpha: 1.0),
        cmdlet: NSColor(srgbRed: 0.03, green: 0.36, blue: 0.76, alpha: 1.0),
        variableNormal: NSColor(srgbRed: 0.75, green: 0.30, blue: 0.05, alpha: 1.0),
        variableScoped: NSColor(srgbRed: 0.64, green: 0.18, blue: 0.22, alpha: 1.0),
        variableAutomatic: NSColor(srgbRed: 0.52, green: 0.22, blue: 0.72, alpha: 1.0),
        parameter: NSColor(srgbRed: 0.16, green: 0.44, blue: 0.52, alpha: 1.0),
        operator: NSColor(srgbRed: 0.36, green: 0.24, blue: 0.58, alpha: 1.0),
        stringLiteral: NSColor(srgbRed: 0.13, green: 0.48, blue: 0.22, alpha: 1.0),
        stringInterpolation: NSColor(srgbRed: 0.76, green: 0.28, blue: 0.06, alpha: 1.0),
        number: NSColor(srgbRed: 0.11, green: 0.44, blue: 0.69, alpha: 1.0),
        typeAttribute: NSColor(srgbRed: 0.06, green: 0.47, blue: 0.47, alpha: 1.0),
        comment: NSColor(srgbRed: 0.40, green: 0.46, blue: 0.52, alpha: 1.0),
        helpTag: NSColor(srgbRed: 0.16, green: 0.49, blue: 0.38, alpha: 1.0),
        regionDirective: NSColor(srgbRed: 0.34, green: 0.39, blue: 0.66, alpha: 1.0),
        invisibles: NSColor(srgbRed: 0.58, green: 0.62, blue: 0.68, alpha: 0.50)
    )

    public static let tahoeMidnight = EditorSyntaxColors(
        canvasBackground: NSColor(srgbRed: 0.094, green: 0.106, blue: 0.133, alpha: 1.0),
        gutterBackground: NSColor(srgbRed: 0.075, green: 0.086, blue: 0.110, alpha: 1.0),
        gutterSeparator: NSColor(srgbRed: 0.18, green: 0.21, blue: 0.26, alpha: 1.0),
        gutterLineNumber: NSColor(srgbRed: 0.44, green: 0.49, blue: 0.58, alpha: 1.0),
        gutterActiveLineNumber: NSColor(srgbRed: 0.47, green: 0.75, blue: 1.00, alpha: 1.0),
        currentLineHighlight: NSColor(srgbRed: 0.38, green: 0.66, blue: 1.00, alpha: 0.085),
        pageGuideHairline: NSColor(srgbRed: 0.60, green: 0.68, blue: 0.82, alpha: 0.12),
        plainText: NSColor(srgbRed: 0.90, green: 0.93, blue: 0.97, alpha: 1.0),
        keyword: NSColor(srgbRed: 0.96, green: 0.49, blue: 0.84, alpha: 1.0),
        cmdlet: NSColor(srgbRed: 0.38, green: 0.73, blue: 1.00, alpha: 1.0),
        variableNormal: NSColor(srgbRed: 1.00, green: 0.72, blue: 0.36, alpha: 1.0),
        variableScoped: NSColor(srgbRed: 1.00, green: 0.53, blue: 0.47, alpha: 1.0),
        variableAutomatic: NSColor(srgbRed: 0.80, green: 0.62, blue: 1.00, alpha: 1.0),
        parameter: NSColor(srgbRed: 0.56, green: 0.85, blue: 0.92, alpha: 1.0),
        operator: NSColor(srgbRed: 0.76, green: 0.66, blue: 0.98, alpha: 1.0),
        stringLiteral: NSColor(srgbRed: 0.52, green: 0.86, blue: 0.58, alpha: 1.0),
        stringInterpolation: NSColor(srgbRed: 1.00, green: 0.78, blue: 0.38, alpha: 1.0),
        number: NSColor(srgbRed: 0.49, green: 0.82, blue: 0.98, alpha: 1.0),
        typeAttribute: NSColor(srgbRed: 0.36, green: 0.88, blue: 0.80, alpha: 1.0),
        comment: NSColor(srgbRed: 0.49, green: 0.56, blue: 0.65, alpha: 1.0),
        helpTag: NSColor(srgbRed: 0.44, green: 0.86, blue: 0.72, alpha: 1.0),
        regionDirective: NSColor(srgbRed: 0.62, green: 0.70, blue: 0.95, alpha: 1.0),
        invisibles: NSColor(srgbRed: 0.50, green: 0.58, blue: 0.70, alpha: 0.42)
    )

    public static let pitchBlackPro = EditorSyntaxColors(
        canvasBackground: NSColor(srgbRed: 0.020, green: 0.024, blue: 0.032, alpha: 1.0),
        gutterBackground: NSColor(srgbRed: 0.010, green: 0.012, blue: 0.018, alpha: 1.0),
        gutterSeparator: NSColor(srgbRed: 0.14, green: 0.16, blue: 0.21, alpha: 1.0),
        gutterLineNumber: NSColor(srgbRed: 0.42, green: 0.46, blue: 0.54, alpha: 1.0),
        gutterActiveLineNumber: NSColor(srgbRed: 0.50, green: 0.80, blue: 1.00, alpha: 1.0),
        currentLineHighlight: NSColor(srgbRed: 0.40, green: 0.70, blue: 1.00, alpha: 0.09),
        pageGuideHairline: NSColor(srgbRed: 0.55, green: 0.65, blue: 0.80, alpha: 0.12),
        plainText: NSColor(srgbRed: 0.94, green: 0.96, blue: 0.99, alpha: 1.0),
        keyword: NSColor(srgbRed: 0.98, green: 0.52, blue: 0.86, alpha: 1.0),
        cmdlet: NSColor(srgbRed: 0.42, green: 0.78, blue: 1.00, alpha: 1.0),
        variableNormal: NSColor(srgbRed: 1.00, green: 0.75, blue: 0.38, alpha: 1.0),
        variableScoped: NSColor(srgbRed: 1.00, green: 0.56, blue: 0.50, alpha: 1.0),
        variableAutomatic: NSColor(srgbRed: 0.83, green: 0.65, blue: 1.00, alpha: 1.0),
        parameter: NSColor(srgbRed: 0.60, green: 0.89, blue: 0.95, alpha: 1.0),
        operator: NSColor(srgbRed: 0.80, green: 0.70, blue: 1.00, alpha: 1.0),
        stringLiteral: NSColor(srgbRed: 0.56, green: 0.90, blue: 0.62, alpha: 1.0),
        stringInterpolation: NSColor(srgbRed: 1.00, green: 0.82, blue: 0.42, alpha: 1.0),
        number: NSColor(srgbRed: 0.54, green: 0.86, blue: 1.00, alpha: 1.0),
        typeAttribute: NSColor(srgbRed: 0.40, green: 0.92, blue: 0.84, alpha: 1.0),
        comment: NSColor(srgbRed: 0.52, green: 0.59, blue: 0.68, alpha: 1.0),
        helpTag: NSColor(srgbRed: 0.48, green: 0.90, blue: 0.76, alpha: 1.0),
        regionDirective: NSColor(srgbRed: 0.66, green: 0.74, blue: 0.98, alpha: 1.0),
        invisibles: NSColor(srgbRed: 0.48, green: 0.56, blue: 0.68, alpha: 0.42)
    )
}

// MARK: - Symbol Kind Badge Color Helper

public func symbolBadgeColor(_ kind: SymbolKind) -> Color {
    switch kind {
    case .function: return .blue
    case .filter: return .cyan
    case .workflow: return .indigo
    case .class: return .teal
    case .configuration: return .purple
    case .scriptblock: return .orange
    case .paramBlock: return .mint
    case .pipelineBlock: return .pink
    case .section: return .secondary
    case .region: return .indigo
    case .configVars: return .orange
    case .dataTable: return .teal
    case .controlBlock: return .purple
    }
}
