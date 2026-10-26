# ERP-094 cumulative release

Date: 2026-10-26. User explicitly authorized merge, push and deployment of ERP-094, including ERP-092/093. PJM held the exclusive write/deploy window; no worktree was created.

## Version and review

- Functional/deployed SHA: `4d6a4941f62d7ff27ef3a77f1d441c32302676cd`.
- Retained ancestors: ERP-093 `4b8e4bbeaf8b4680090275c289554ac17a74954f`, ERP-092 `3b54babbc26c2bf14c7604aa3b83aa596f34bfc1`.
- Previous main: `50e598575a481942cdd0b800bb2b9ebb4d2e17b9`.
- Clean checkout and fetched remote ancestry verified; main fast-forwarded and pushed without history rewriting or moving tags. This report is a subsequent documentation-only commit, not another application build.
- Reviewed handoffs, cumulative 74-file scope, cost calculation/version/permission boundaries, order allocation locking/idempotency, invoice history preservation and V42–V44 migration behavior. No release-blocking defect identified in this review; this is not a claim of exhaustive real-business workflow acceptance.

## Independently executed gates

- Backend `./mvnw clean verify`: BUILD SUCCESS; 213 tests, 0 failures, 0 errors, 1 existing conditional OSS connectivity skip. Log: `/tmp/erp094-release-backend.log`.
- Frontend `ERP_BUILD_OUT_DIR=/tmp/erp094-release-frontend pnpm quality`: 77 files / 316 tests, lint, types and production build passed. Log: `/tmp/erp094-release-frontend.log`.
- Cumulative `git diff --check` passed.

## Protection and deployment

Backend writes were stopped before taking fresh protected backups. Original database, resources and old release directories were retained. Backup directory: `.local/backups/erp094-release/` (files mode 600).

| Backup | SHA-256 |
| --- | --- |
| pre-upgrade.sql.gz | cd60b87f5cc92f8d1d198f2e683f4eef850073a9a09d348d225d18905d0bf32c |
| resources.tar.gz | a7ab54b27ccdc0cf1686175a5526ab74182928beadf715afbdcd03b34ece456f |
| runtime-config.tar.gz | 83773780455e9ee3cd69dad56cbc3a241927a4e132d181da1fe7a4343db37e3e |

Database dump gzip integrity passed. This release did not repeat an isolated restore drill; the implementation handoff's isolated MySQL tests remain separately attributed evidence.

- Existing `.local/runtime/run-backend.sh` restarted in `jiugeng-mvp:0.0`, retaining MySQL/OSS configuration.
- Actual persistent MySQL migrated V41 → V42 → V43 → V44 successfully; schema history confirms all three success flags. Startup log `/tmp/erp094-runtime.log`.
- Tested frontend copied into `.local/releases/frontend/4d6a4941f62d7ff27ef3a77f1d441c32302676cd`; persistent Caddy wrapper updated and Caddy restarted. Old frontend retained. Vite alone was not treated as public deployment.
- Existing public tunnel retained: https://andreas-reel-proceedings-problems.trycloudflare.com/product-costs

## Acceptance

- Backend `/actuator/health`: HTTP 200 / UP; mysql profile startup confirmed.
- Local Caddy/public HTML and entry JS bytes match; HTML/API no-store, hashed JS immutable.
- Anonymous cost API: 401.
- Synthetic ADMIN and PRODUCT_MATERIAL logins: both expose `costs.read`; public GET `/api/v1/product-costs` and `/api/v1/product-costs/operations`: 200 for both.
- Static router and navigation group mapping verified: 商品 → 产品管理 → 产品成本. **Browser menu-click acceptance is NOT completed.** Initial browser tooling was unavailable; after installing the browser, one attempt failed and the subsequent retry was interrupted without a recoverable final result. Neither attempt is recorded as a browser pass. Successful public HTML/API checks above do not substitute for rendered browser navigation acceptance.
- Final cleanup inspection found one enabled synthetic QA account and one session left by the interrupted browser retry. PJM disabled that exact account and deleted only its session records. Follow-up queries confirm **0 enabled ERP-094 QA accounts and 0 ERP-094 QA sessions**; all four synthetic QA account records remain disabled. Audit records retained. No real business data writes or cost publication were performed; no credentials committed or printed.
- Final closeout only updated this report and cleaned the interrupted QA probe. No repeated deployment, migration, service restart or business data modification. Backend health rechecked: UP.

## Remaining boundaries

Cost CSV has no image/PDF output; same-parent copying covers first 100 SKUs; stale/incomplete filtering has no large-scale performance guarantee. Order invoices do not support void/red reversal; an existing pending invoice blocks order cancellation/closure. No external tax/1688/logistics integration. Rollback must respect V42–V44 and any new order/cost data: do not overwrite newer business data with the pre-upgrade backup.
