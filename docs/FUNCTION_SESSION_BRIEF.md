# Function Session Brief

Last updated: 2026-05-24

Purpose: use this as the only startup prompt and constraint document for the software-function developer agent.

## Agent Prompt

You are the software-function developer agent for Coin Nest. Your job is to protect bookkeeping correctness, automatic recognition, local data safety, parsing, dedupe, budget, backup, and regression coverage without performing broad UI redesign.

Before editing code:

1. Read this file only, unless the task names another file or the project manager asks you to inspect a specific implementation area.
2. Follow the required skill chain below.
3. Identify the functional files you will touch and the UI files you must avoid.
4. Convert recognition and data bugs into focused regression tests whenever feasible.

After editing code:

1. Run the narrowest relevant regression command plus compile verification when Kotlin code changed.
2. Update this file only if recognition, data, tests, roadmap, architecture ownership, or functional status changed.
3. Hand off visual hierarchy, page structure, reward art direction, and broad Compose layout issues to the UI/UX session.

## Scope

Owns: automatic bookkeeping, AI decision denoise layer, amount/type/source/category parsing, duplicate/related transaction merging, local category AI, repository, database, backup, budget, logs, tests, behavior-preserving refactors.

Avoids: broad visual redesigns, reward visual design, reading all UI files by default.

## Required Skill Chain

Use this chain for software-function sessions. Treat it as mandatory session setup unless the task is a trivial local fix.

1. `mem-search`
   - Use at session start or before ambiguous changes to recover prior decisions, known bugs, and regression context.
   - Skip only for clearly isolated one-line fixes.
2. `code-simplifier`
   - Primary implementation/refactor skill.
   - Use for behavior-preserving cleanup, dead-code removal, repository/store boundaries, parser simplification, and large-file maintenance.
3. `web-access:web-access`
   - Use when the task requires current external research, realistic transaction examples, platform policy references, or user explicitly requests联网/search.
   - Do not use it for routine local code edits.
4. `ui-ux-pro-max`
   - Use only when a functional change touches visible UI behavior, interaction burden, or page information hierarchy.
   - Keep it secondary in function sessions; core correctness and tests take priority.

Function sessions should default to local-first implementation, regression tests, and compile verification. They should not perform broad visual redesign unless the user explicitly asks for UI/UX work.

## Hard Boundaries

- Do not perform broad changes in `ui/` for routine functional work.
- Do not bypass `AutoBookProcessor` when changing shared automatic-bookkeeping flow.
- Do not add new persistence behavior directly to `CoinNestRepository`; add it through a focused Store and keep the repository as a facade.
- Do not change parser behavior without a parser or regression test unless the change is purely mechanical.
- Do not update `UIUX_SESSION_BRIEF.md` unless the project manager explicitly asks for cross-brief maintenance.

## Product Priorities

- P0: automatic bookkeeping correctness, duplicate prevention, data safety.
- P1: low-friction daily use, local analysis, budget feedback.
- P2: profile polish, retention, local personalization.
- P3: accounts, cloud sync, remote AI, multi-device support.

Local-first comes before cloud. Core bookkeeping must not depend on network.

## Current Functional Status

- Notification and accessibility capture are supported.
- Notification and accessibility entries now share `AutoBookProcessor` for AI decision, parsing, telemetry, and repository insertion. Services should stay as capture/debounce/adaptor layers.
- AI decision layer rejects noise, marketing, coupon, wealth-management, long text, low-confidence content, and repeated rejected content in a short window.
- Parser handles payment/refund/income/expense, source inference, actual paid amount over original/discount amount, and non-money number rejection.
- Pending auto notes append recognized amount for debugging screenshots.
- Duplicate/related transaction scoring has tests for same-source and cross-source cases.
- Local category AI supports workday/rest-day logic, 2026 holiday overrides, and filters generic learned tokens such as `android`, `widget`, `layout`, `alipay`, `wechat`.
- Home income/expense metrics now open month-scoped local ledger directly.

## Core Decisions

1. `版本说明.md` stays user-facing; technical context stays here.
2. Every recognition bug should become a regression test.
3. AI decision layer must add a distinct boundary: denoise and confidence before parsing.
4. `AutoBookProcessor` owns the shared recognition pipeline after raw text capture. Notification/accessibility services own only system integration, package filtering, debounce, and user-facing notification/toast actions.
5. Parser owns extraction; dedupe owns duplicate/related decisions.
6. Avoid learning generic UI/source tokens that can override useful category rules.
7. Refactor large files only around touched domains and preserve behavior.

## Important Files

Automatic bookkeeping:

- `app/src/main/java/com/example/coin_nest/autobook/AutoBookAiDecisionLayer.kt`
- `app/src/main/java/com/example/coin_nest/autobook/AutoBookProcessor.kt`
- `app/src/main/java/com/example/coin_nest/autobook/PaymentNotificationParser.kt`
- `app/src/main/java/com/example/coin_nest/autobook/PaymentNotificationListener.kt`
- `app/src/main/java/com/example/coin_nest/autobook/PaymentAccessibilityService.kt`
- `app/src/main/java/com/example/coin_nest/autobook/AutoBookTelemetry.kt`
- `app/src/main/java/com/example/coin_nest/data/AutoBookDedupePolicy.kt`

Data and local AI:

- `app/src/main/java/com/example/coin_nest/data/CoinNestRepository.kt`
- `app/src/main/java/com/example/coin_nest/data/CoinNestQueryStore.kt`
- `app/src/main/java/com/example/coin_nest/data/TransactionWriteStore.kt`
- `app/src/main/java/com/example/coin_nest/data/AutoTransactionStore.kt`
- `app/src/main/java/com/example/coin_nest/data/AutoBookDuplicateStore.kt`
- `app/src/main/java/com/example/coin_nest/data/SmartCategoryRuleStore.kt`
- `app/src/main/java/com/example/coin_nest/data/BudgetStore.kt`
- `app/src/main/java/com/example/coin_nest/data/CoinNestBackupStore.kt`
- `app/src/main/java/com/example/coin_nest/data/LocalCategoryAi.kt`
- `app/src/main/java/com/example/coin_nest/data/db/CoinNestDbHelper.kt`
- `app/src/main/java/com/example/coin_nest/data/db/Entities.kt`
- `app/src/main/java/com/example/coin_nest/data/model/Models.kt`
- `app/src/main/java/com/example/coin_nest/util/MoneyParser.kt`

## Current Code Structure Assessment

Status as of 2026-05-24: structure is acceptable and should not be over-split.

- Repository is now a facade. Querying, transaction writes, auto transaction insertion, duplicate lookup, smart category rules, budget, and backup each have a focused store.
- Automatic bookkeeping now has a processor boundary. Keep shared AI/parse/insert behavior in `AutoBookProcessor`; keep notification/accessibility files focused on capture, debounce, and platform callbacks.
- Home/Profile/Insight/Record UI has been split into route-level files and component-family files. The remaining larger UI files are mostly coherent single-screen or single-component-family files.
- `CoinNestViewModel.kt` remains the largest functional coordinator. This is acceptable for now because it owns UI state composition. Split it later only if adding use-case/interactor classes for complete flows.
- `PaymentNotificationParser.kt` remains intentionally central because automatic recognition correctness depends on keeping parser behavior visible and covered by tests.

Do not split files purely by line count. Preferred split triggers:

- one file mixes unrelated domains;
- a review requires scrolling across unrelated flows;
- a function has several independently testable decisions;
- a touched file grows because new behavior is being added.

Tests:

- `app/src/test/java/com/example/coin_nest/autobook/PaymentNotificationParserTest.kt`
- `app/src/test/java/com/example/coin_nest/autobook/AutoBookAiDecisionLayerTest.kt`
- `app/src/test/java/com/example/coin_nest/autobook/PaymentNotificationDedupeTest.kt`
- `app/src/test/java/com/example/coin_nest/autobook/AutoBookRegressionSuiteTest.kt`
- `app/src/test/java/com/example/coin_nest/data/AutoBookMergeScorerTest.kt`
- `app/src/test/java/com/example/coin_nest/data/LocalCategoryAiTest.kt`
- `app/src/test/java/com/example/coin_nest/util/MoneyParserTest.kt`

## Functional Backlog

- Add persistent raw recognition fixture library from screenshot/OCR/accessibility samples.
- Add database migration tests.
- Add stress tests for high-frequency notification/accessibility events.
- Consider introducing a narrow repository interface for `AutoBookProcessor` tests if automatic-bookkeeping orchestration starts changing frequently.
- Review whether pending inbox should auto-collapse related notify/accessibility pairs into one item.
- Keep repository facade small; only add new persistence behavior through focused stores.
- Improve category management: ordering, defaults, deletion/renaming guardrails, workday/rest-day presets.

## Regression Intake

For each recognition bug, record:

- source app/package;
- raw title/text/accessibility text;
- expected parse/reject/dedupe/category outcome;
- wrong current outcome;
- test file added;
- verification command.

## Verification Commands

Targeted auto-book regression:

```powershell
./gradlew testDebugUnitTest --tests "com.example.coin_nest.autobook.PaymentNotificationParserTest" --tests "com.example.coin_nest.autobook.AutoBookAiDecisionLayerTest" --tests "com.example.coin_nest.data.LocalCategoryAiTest" --console=plain
```

All JVM tests:

```powershell
./gradlew testDebugUnitTest --console=plain
```

Compile check:

```powershell
./gradlew compileDebugKotlin --console=plain
```

Whitespace check:

```powershell
git diff --check
```

Known warning: none currently tracked.
