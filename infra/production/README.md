# Production deployment

This configuration supports one existing Ubuntu 24.04 host with Java 21, MySQL 8,
Caddy and a separately mounted `/srv/jiugeng` data volume. Builds run on GitHub,
not on the application server. No paid runner, registry or cloud backup is required.

## Release gates

`Production` runs only for `main` and only with repository variable
`PRODUCTION_DEPLOY_ENABLED=true`. The project manager merging the reviewed PR
into main is the release approval; there is no additional environment approval.
The server additionally requires `/etc/jiugeng/deploy-enabled`.
Keep both gates disabled until final data migration and public access acceptance.
The project manager reviews and merges changes to main; this setup does not merge.

The workflow reruns secret scanning, backend verify, frontend quality, import
contracts and deployment tooling tests before packaging the exact commit.
Environment secrets: `PRODUCTION_SSH_HOST`, `PRODUCTION_SSH_KEY`, and
`PRODUCTION_KNOWN_HOSTS`. Pin the independently verified SSH host key.
The environment must restrict deployment branches to main, with no required
reviewers or wait timer. Keep the main branch PR and required-check protections.

## Host setup

Install `openjdk-21-jre-headless mysql-server caddy python3 curl ca-certificates rsync`.
Do not format disks without verifying that they are empty and assigned to this app.
Mount the data volume persistently by UUID. Create a non-login `jiugeng` service
user and root-owned `/srv/jiugeng/releases`, `/srv/jiugeng/backups` (0700).
Only `/srv/jiugeng/data` is writable by the application.

Configure MySQL on loopback, an application database and a database-specific user.
Use a 384 MiB buffer pool, max 40 connections, and application pool size 5.
Store secrets in `/etc/jiugeng`; never put them in Git or command output.
`backend.env.example` documents the required paths. Copy the original `user_defaults_key` (32-byte Base64 AES key) into the
configtree secret directory as well; encrypted default passwords require the same
key as the source database. Preserve it across deployments, with mode 0600 and
application ownership. Never regenerate it when restoring existing encrypted data.
Copy existing OSS credentials
securely and keep the existing bucket; do not create a paid storage service.
Copy catalog/dashboard image directories and the private forwarder templates.
Import a consistent database snapshot and invalidate imported login sessions.
Keep Amazon collection disabled during rehearsal to avoid dual collectors.

Install `jiugeng.service` in `/etc/systemd/system`. Install production scripts in
`/usr/local/lib/jiugeng`, owned by root and executable. Create `erp-deploy`, with a
root-owned home and `.ssh` directory. Its sole key must be configured as:

```
restrict,command="/usr/local/lib/jiugeng/ssh-entry.sh" ssh-ed25519 PUBLIC_KEY
```

Give this account sudo access only to `/usr/local/lib/jiugeng/receive.sh`.
The root receiver validates arguments, gates, checksums, archive paths and manifest;
SSH does not provide an interactive shell, port forwarding or general sudo access.
Revoke the temporary bootstrap administrator key when preparation ends.

## Rehearsal and cutover

1. Deploy the tested main revision and use `Caddyfile.preflight` on loopback only.
2. Verify backend health and `/version.json`, login, permissions, exports, pictures
   and attachment access. Use a synthetic account and revoke its sessions afterward.
3. Compare database and file counts. A rehearsal copy is not the live source of truth.
4. Confirm mainland hosting/ICP eligibility, domain routing and HTTPS before public
   cutover. Do not change public DNS to an unready service.
5. Agree a short write pause. Stop local business writers and collectors, create a
   fresh database backup, sync the final files, restore to cloud and revoke sessions.
6. Repeat business smoke checks, then activate the public Caddy configuration. Keep
   the loopback 4173 probe from `Caddyfile.live.example` for deployment health checks.
7. Verify HTTPS/login from outside. Enable collection only on the accepted host.
8. Enable both deployment gates after acceptance. Keep local data as a recovery
   copy; do not operate both copies as writers. If rolling back after cloud writes,
   reconcile/export those writes first; never blindly restore an older snapshot.

Do not enable application bootstrap defaults or leave synthetic QA accounts active.

## Deployment failure behavior

The receiver takes an exclusive lock, validates the package before stopping the
application, and backs up the database before changing the current release symlink.
It waits up to three minutes for health and verifies the served frontend revision.
If schema history is unchanged it restores the previous application and checks
health. If migrations changed the schema, it stops for a forward fix; it never
silently restores an older database. Inspect the journal and backup before recovery.
An already existing release directory is intentionally rejected: repeated deployments
of one SHA require an operator inspection, not an overwrite of immutable releases.

## Backups and limits

Install `jiugeng-backup.service` and `.timer`; the timer runs daily at 03:30 server
time plus up to five minutes jitter. Deployments also take database backups.
Backups are compressed, validated and accompanied by SHA-256 checksums. Verify
restoration periodically into an isolated temporary database, never over production.
Monitor backup service failures and disk space. Backups are retained until an operator
sets an appropriate retention policy; the deployment receiver refuses low disk space.

These are database backups on the same disk, not disaster recovery. Local image files,
private templates and encrypted configuration need a separate offline copy before
cutover; existing OSS objects remain in OSS. No off-host backup subscription is created.
The current host's memory and limited bandwidth require monitoring; do not run builds
there. Public acceptance, live collection, final data sync, and actual main deployment
must be recorded separately from successful private rehearsal.
