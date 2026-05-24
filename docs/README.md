# Coin Nest Project Docs

Keep this folder small. `版本说明.md` is for users; `docs/` is for development context that must survive long tasks and new sessions.

## Read Only What You Need

- UI/UX work: read [UIUX_SESSION_BRIEF.md](UIUX_SESSION_BRIEF.md).
- Software-function work: read [FUNCTION_SESSION_BRIEF.md](FUNCTION_SESSION_BRIEF.md).

Do not load every Markdown file by default. The two brief files are designed to be the only startup context for focused sessions.

## Agent Routing

Use this README as the project-manager / technical-director entry point. Route specialist sessions by responsibility:

- UI/UX developer agent: start from `docs/UIUX_SESSION_BRIEF.md` and follow its required skill chain before touching UI code.
- Software-function developer agent: start from `docs/FUNCTION_SESSION_BRIEF.md` and follow its required skill chain before touching functional code.
- Architecture manager: may read both brief files, decide boundaries, and update this README when ownership or structure changes.

Do not ask both specialist agents to edit the same file in parallel. If a task crosses UI and function boundaries, define the data/action contract first, then assign UI consumption and function implementation separately.

## Specialist Prompt Rules

When starting a specialist session, give the agent only its matching brief plus the concrete task. The brief is the role prompt and constraint document.

- UI/UX sessions must not change parser, dedupe, database, repository, Worker, notification listener, accessibility service, or backup behavior unless the task is explicitly reclassified.
- Function sessions must not perform broad visual redesign, style-system changes, reward art direction, or page restructuring unless the task is explicitly reclassified.
- Both sessions must update only their own brief when facts change. Keep `版本说明.md` user-facing and concise.
- If a required skill in a brief is unavailable, the agent must state the missing skill and continue with the closest local fallback instead of inventing external context.

## Update Rules

1. After UI/UX work, update `UIUX_SESSION_BRIEF.md` only if the page structure, design direction, or UI status changed.
2. After software-function work, update `FUNCTION_SESSION_BRIEF.md` only if recognition, data, tests, roadmap, or functional status changed.
3. Convert every automatic-recognition bug into a regression test.
4. Keep docs concise. Prefer replacing stale notes over adding new files.
5. Keep `版本说明.md` simple and user-facing.

## Current Structure Snapshot

Last checked: 2026-05-24.

The project is now reasonably split. Do not keep splitting only because a file is above 300 lines; split only when a file mixes unrelated responsibilities or a touched area becomes hard to review.

- `autobook/`: automatic recognition pipeline, AI decision denoise, notification/accessibility capture, telemetry.
- `data/`: repository facade plus focused stores for query, transaction writes, auto transaction insertion, duplicate detection, smart category learning, budget, backup.
- `ui/`: app shell, tab routes, route-specific cards/components, profile/reward components, insight/search/detail routes.
- `util/`, `budget/`, `widget/`: small supporting domains.

Current acceptable larger files:

- `CoinNestViewModel.kt`: still the UI orchestration hub. Split only when moving complete flows into use-case/interactor classes.
- `HomeInsightOverview.kt`, `HomeRecordTab.kt`, `HomeUiUtils.kt`, `HomeProfileRewardComponents.kt`: each is mostly one coherent UI/component family. Do not split further unless changing that area.
- `AutoBookProcessor.kt`: shared automatic-bookkeeping pipeline for notification/accessibility inputs. Entry services should collect raw text and keep channel-specific debounce only.
- `PaymentNotificationParser.kt`: core parser; split only around well-tested parser subdomains.
