# SHADOW MONEY — Architecture Documentation

## Phase 1 — Android Foundation

### Overview

SHADOW MONEY is a personal Android financial-management application. Phase 1 establishes the Android application foundation.

### Package Structure

```
com.prasbin.shadowmoney
├── data                    # Room database, DAOs, repositories
├── domain                  # Models, use cases, financial calculators
├── presentation            # Screens, components, navigation
│   ├── theme              # Material 3 dark futuristic theme
│   ├── navigation         # Navigation graph and routes
│   └── screen             # Individual screen composables
├── utils                   # Extensions, helpers, constants
└── di                      # Dependency injection setup
```

### Theme

Dark futuristic "System" aesthetic:
- Dark backgrounds (#0A0A1A, #12121F, #1A1A2E)
- Neon accents (Cyan #00D4FF, Purple #7B68EE)
- Monospace font for financial numbers
- Professional cards/panels with borders
- Readable, financial-focused UX

### Database

Room database foundation established. Schema version 1.
Financial tables (accounts, transactions, categories, goals) will be added in Phase 2.

### Navigation

Bottom navigation with sections: Dashboard, Money, Settings.
Additional sections (Transactions, Jobs, Work, Opportunities) registered as placeholders.

### Dependencies

Same versions as SHADOW LEARN project for consistency:
- AGP 8.13.2
- Kotlin 2.1.20
- Compose BOM 2026.04.01
- Room 2.8.3
- Navigation Compose 2.7.7
- DataStore Preferences 1.1.1
- WorkManager 2.9.0

### Security

- No secrets in source
- No API keys
- No analytics SDK
- Local-only data
- Exact integer monetary representation

## Planned Architecture (Future Phases)

- Room entities: Account, Transaction, Category, Goal, Job, Opportunity, Telecom, Budget
- Repository pattern for all data access
- Use cases for financial calculations
- Deterministic intelligence engine
- CSV import pipeline
- Local financial assistant
- Backup/restore with SAF
