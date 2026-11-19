//
//  Expense.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData
import SwiftUI

@Model
final class Expense {
    var amount: Decimal
    var categoryRaw: String
    var date: Date
    var note: String
    /// Photo or scan of the bill/receipt, stored outside the main database file.
    @Attribute(.externalStorage) var receiptData: Data?

    var category: ExpenseCategory {
        get { ExpenseCategory(rawValue: categoryRaw) ?? .other }
        set { categoryRaw = newValue.rawValue }
    }

    init(amount: Decimal, category: ExpenseCategory, date: Date = .now, note: String = "") {
        self.amount = amount
        self.categoryRaw = category.rawValue
        self.date = date
        self.note = note
    }
}

enum ExpenseCategory: String, CaseIterable, Identifiable {
    case food, transport, bills, learning, other

    var id: Self { self }

    var title: String { rawValue.capitalized }

    var symbol: String {
        switch self {
        case .food: "fork.knife"
        case .transport: "car.fill"
        case .bills: "doc.text.fill"
        case .learning: "graduationcap.fill"
        case .other: "ellipsis.circle.fill"
        }
    }

    var color: Color {
        switch self {
        case .food: .orange
        case .transport: .blue
        case .bills: .purple
        case .learning: .green
        case .other: .gray
        }
    }
}
