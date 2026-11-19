//
//  ChipRow.swift
//  MyPersonalTracker
//

import SwiftUI

/// A horizontally scrolling row of tappable capsules.
struct ChipRow: View {
    let items: [String]
    let selected: (String) -> Bool
    let onTap: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.self) { item in
                    let isSelected = selected(item)
                    Button(item) { onTap(item) }
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .foregroundStyle(isSelected ? Color.white : Color.primary)
                        .background(isSelected ? Color.accentColor : Color(.tertiarySystemFill), in: .capsule)
                        .buttonStyle(.plain)
                }
            }
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
    }
}

enum ActivitySuggestions {
    /// Recently used names, then Learning sections, then topics currently being learned; case-insensitively unique.
    static func make(pastSessions: [StudySession], sections: [LearningSection], limit: Int = 12) -> [String] {
        let recent = pastSessions.map(\.title)
        let sectionNames = sections.map(\.name)
        let learning = sections.flatMap(\.topics).filter { $0.status == .learning }.map(\.title)
        var seen = Set<String>()
        return (recent + sectionNames + learning)
            .filter { seen.insert($0.lowercased()).inserted }
            .prefix(limit)
            .map { $0 }
    }
}
