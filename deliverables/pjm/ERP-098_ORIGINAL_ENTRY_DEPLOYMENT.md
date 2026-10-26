# ERP-098 original-entry deployment record

Deployment window: 2026-10-26 23:54–2026-10-27, Asia/Shanghai. User required the existing local system and explicitly handed shared-8080 deployment to PJM. Shared public API interruption was disclosed before execution. No public static frontend publication, main merge or remote push was performed.

## Exact version and runtime

- Application SHA: `720fd0bb77855920d6484aa57708fcc0dceda0a3` on `feature/ERP-098-selection-monitoring`.
- Original UI: http://127.0.0.1:5173/product-monitoring ; original API: port 8080.
- Original database retained: `jiugeng_erp`; no isolated database import or proxy replacement.
- Persistent backend wrapper `.local/runtime/run-backend.sh` runs the independently built fixed JAR `.local/releases/backend/720fd0bb77855920d6484aa57708fcc0dceda0a3/backend.jar`, SHA-256 `635db2b422c198c2d9532b9b91f18adaf26f29f6df357b92eced682fa5108f7a`.
- Original auth, MySQL, OSS and resource configuration retained. Public Amazon adapter explicitly enabled, using the existing local outbound proxy mechanism; no credentials printed or changed.
- Caddy static root and tunnel were not changed. Public API shares 8080 and therefore now uses the upgraded backend. The old public static UI was not accepted as a new-version frontend.

## Independent review and gates

- Reviewed missing-field partial observation merge, evidence persistence, bounded/cancellable HTTP body, public-search coverage safeguards and V45–V48 schema delta.
- `./mvnw clean verify`: 228 tests, 0 failures, 0 errors, 1 existing optional OSS connectivity skip. `/tmp/erp098-pjm-backend.log`.
- `ERP_BUILD_OUT_DIR=/tmp/erp098-pjm-frontend pnpm quality`: 81 files / 330 tests; lint/types/build passed. `/tmp/erp098-pjm-frontend.log`.
- Cumulative diff check passed; handoff checkout matched exact SHA and was clean.

## Backups, restore rehearsal and preservation

Protected backups: `.local/backups/erp098-original/`. Original service was stopped for final database/resource/config backups before migration. No original data was cleared.

| File | SHA-256 |
| --- | --- |
| pre-upgrade.sql.gz | b4d732b4b6571be737a4433ddb7bef18c0a0d4c3d2c7e409b00d57dd1ed1beb6 |
| resources.tar.gz | 0e0aea8fa31809ff879ea109755a14b9bec046f39c608dbae17e9a106b7796c9 |
| runtime-config.tar.gz | a48c66d6191f1cc03e7d4babbb438637d12a67ca23ba857f5682657d1c4fbc6f |

- A fresh pre-maintenance snapshot was restored into a separate MySQL8 container, `jiugeng-erp098-original-rehearsal`; candidate JAR upgraded that restored original schema V44→V48 and reached UP on temporary port18098. Original 69 table counts were compared: only Flyway history changed; all pre-existing business table counts matched.
- Restored resource archive into a separate protected directory; all 374 restored file hashes matched original files. Database gzip integrity passed.
- Temporary rehearsal backend and container stopped; protected restored data retained, not exposed as a user entry.
- Actual original DB V45/V46/V47/V48 applied successfully. Before/after counts again matched across all pre-existing tables except migration history. Baseline: 28 users, 7 products, 10 variants, 1 monitor target, 0 monitored stores. Counts are not a claim of full row-by-row database equality.
- Immediately after upgrade: 0 collection schedules; original monitoring target remained paused. No blanket scheduling, isolated sample import, or modification of the user's target was performed.

## Actual acceptance and remaining check

- Original backend health HTTP200 / UP; V48 migration startup evidence in `/tmp/erp098-original-runtime.log`.
- Original5173 monitoring page HTTP200; unauthenticated collection API401; existing public login page200.
- Three temporary synthetic roles ADMIN/PRODUCT_MATERIAL/SELECTION authenticated against original8080. GET collection status, product monitoring, watch stores and selection candidates succeeded; source status exposed enabled `amazon-public-us`.
- Playwright against original5173 completed actual login, product monitoring rendering and unified 上新监控 menu checks for all three roles; no 5188 link or separate 店铺监控 menu. `/tmp/erp098-original-smoke.log`.
- Three synthetic QA records were disabled and sessions removed; final enabled-QA/session counts both0. Their disabled account/security audit records remain. This explains the subsequent +3 user rows compared with pre-acceptance counts; no business entity writes were made by these checks.
- During PJM's initial deployment window, original-account acceptance was pending. Safari JavaScript inspection was unavailable; PJM did not change browser security settings or extract session credentials. Codex was assigned browser-only acceptance without checkout changes or service operations; results were returned on 2026-10-27 as follows.
- **Original-account product browser chain passed (Codex browser evidence):** existing authenticated ADMIN at original5173 refreshed, explicitly selected and enabled the original paused target through visible UI, and saw automatic collection plus page refresh. Actual title, main image, brand, AVAILABLE status, external `amazon-public-us` source and observation time `2026-10-27 00:02:19` were displayed. Unobserved price remained absent; PARTIAL was reported rather than a fabricated complete snapshot. No404 or alternate-port link remained.
- **Original-account store creation and durable result passed (Codex browser/DB evidence):** the same account added the public reference seller through original5173 `/watch/stores`, using the actual name/URL form. Automatic scheduling followed; original DB reported STORE SUCCEEDED and23 public-source presence records. No isolated sample database was imported. Real seller identity/URL are intentionally not reproduced in this report.
- **PJM independent read-only corroboration after handoff:** original database contains PRODUCT PARTIAL ×1, STORE SUCCEEDED ×1 and `amazon-public-us` presence records ×23. This confirms original-environment durable outcomes, not independent observation of every browser interaction.
- Store-detail final browser inspection was not completed because the user took control of Safari; Codex stopped immediately. No browser settings or session credentials were changed/extracted, and no checkout files/services were changed during browser acceptance. Store-detail final rendering remains an explicit open check.

## Status and restrictions

Original-entry deployment, synthetic-role compatibility, original-account product collection/display, and store creation/durable collection are confirmed with the attribution above. User delivery points ONLY to original5173;5188 is not a delivery entry. ERP-098/P0-A/P0-B are not declared fully accepted. Store-detail final rendering, multi-day/large-store/stability evidence and previously recorded P0-A UI/evidence closure remain pending. Public search coverage is not exhaustive seller inventory and must not infer delisting. Post-acceptance changes to the original target, store and observation/task records are explicit browser acceptance outcomes, distinct from the unchanged pre/post-migration counts.

Do not restore an old database over new business writes. Preserve the backup and use reviewed forward fixes if the new schema has received new data. This document is a separate local documentation commit; runtime stays pinned to the application SHA above. No main merge/push/public-static release is implied.
