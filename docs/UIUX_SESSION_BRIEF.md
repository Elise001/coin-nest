# UI/UX Session Brief

Last updated: 2026-05-24

Purpose: use this as the only startup document for UI/UX work.

## Scope

Owns: page structure, information hierarchy, visual style, spacing, typography, colors, component states, interaction paths, transition quality, mobile ergonomics, profile/reward UX.

Avoids: parser rules, dedupe, database, repository behavior, broad backend refactors.

## Product Direction

- Local-first personal bookkeeping for users who want low-burden spending awareness.
- Core promise: less manual input, clearer spending status, reliable abnormal spending awareness.
- UI tone: young, clean, low-pressure, readable before decorative.
- Rewards can help retention, but must stay secondary to bookkeeping and insight.

## Current Page Roles

- Home: monthly wallet, balance, income/expense/net value, budget forecast, today transactions, quick entry, calendar entry.
- Record: quick manual entry, workday/rest-day presets, quick amounts, pending auto inbox.
- Insight: conclusion-first analysis, category focus, local AI spending profile, local ledger/search, anomalies.
- Profile: account console, permissions, budgets, categories, backup, recognition log, profile/avatar, rewards.

## Recent UI Status

- Bottom tabs removed border, top separator, and icon indicator line.
- Home income/expense metrics open local ledger directly.
- Home income/expense entries use month scope, not year scope.
- Budget empty state jumps to profile budget settings.
- Insight page no longer uses a heavy top-level week/month/year switcher.
- Existing preference: improve existing flows before creating duplicate pages.

## Important UI Files

- `app/src/main/java/com/example/coin_nest/ui/HomeScreen.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeRecordTab.kt`
- `app/src/main/java/com/example/coin_nest/ui/RecordPendingInbox.kt`
- `app/src/main/java/com/example/coin_nest/ui/RecordQuickControls.kt`
- `app/src/main/java/com/example/coin_nest/ui/RecordMomentumCard.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeInsightTab.kt`
- `app/src/main/java/com/example/coin_nest/ui/InsightOverviewRoute.kt`
- `app/src/main/java/com/example/coin_nest/ui/InsightSearchRoute.kt`
- `app/src/main/java/com/example/coin_nest/ui/InsightDetailRoutes.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeInsightOverview.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeInsightSearch.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeSettingsTab.kt`
- `app/src/main/java/com/example/coin_nest/ui/SettingsOverviewRoute.kt`
- `app/src/main/java/com/example/coin_nest/ui/SettingsAutoBookRoute.kt`
- `app/src/main/java/com/example/coin_nest/ui/SettingsBudgetRoute.kt`
- `app/src/main/java/com/example/coin_nest/ui/SettingsProfileRoutes.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeSharedComponents.kt`
- `app/src/main/java/com/example/coin_nest/ui/HomeTransactionRows.kt`
- `app/src/main/java/com/example/coin_nest/ui/theme/Color.kt`
- `app/src/main/java/com/example/coin_nest/ui/theme/Theme.kt`

## UI Structure Assessment

Status as of 2026-05-24: UI structure is sufficiently split.

- Tab shell files should stay as navigation/state orchestration.
- Route files own one page or one detail flow.
- Component-family files can remain larger when they are visually cohesive, such as insight overview cards or profile reward components.
- Avoid creating one file per tiny composable; that makes UI changes harder to trace.

## Design Rules

1. Keep interaction paths direct; metric taps should not show intermediate pages.
2. Avoid text-heavy explanations inside primary cards.
3. Keep debug information in recognition logs, not normal user surfaces.
4. Avoid nested-card clutter.
5. Empty states should provide one clear next action.
6. Touch targets must remain comfortable on mobile.
7. Reward visuals should unlock aesthetic/practical value, not only text badges.

## UI Backlog

- Manual QA pass for the four tabs after each visual batch.
- Continue profile reward design only if rewards are meaningful and not noisy.
- Improve dark mode after the core light-mode hierarchy is stable.
- Keep splitting UI files only when a touched screen grows too large.

## Validation

Run after Compose changes:

```powershell
./gradlew compileDebugKotlin --console=plain
```

Check manually:

- no text overlap on narrow screens;
- selected states are clear but not noisy;
- transitions do not reveal accidental intermediate screens;
- Home, Record, Insight, Profile keep distinct roles.
