#!/usr/bin/env python3
"""Copy legacy resources to private OSS; no database updates or source deletions.

Install oss2 and pymysql in an isolated virtualenv. Configuration uses ERP_OSS_*
and ERP_MIGRATION_* environment variables. Default mode is read-only dry-run.
The caller must arrange a quiet write slot before --apply.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import sys


def credential_file(path):
    file = Path(path)
    if file.is_symlink() or not file.is_file() or file.stat().st_size > 4096:
        raise ValueError("Invalid credential file")
    if file.stat().st_mode & 0o077:
        raise ValueError("Credential permissions must be owner-only")
    text = file.read_text(encoding="utf-8")
    values = []
    for field in ("accessKeyId", "accessKeySecret"):
        match = re.search(r"^\s*" + field + r"\s*[:=：]?\s+(\S+)\s*$", text, re.I | re.M)
        if not match:
            raise ValueError("Invalid credential format")
        values.append(match.group(1))
    return values


def safe_file(directory, key):
    if not re.fullmatch(r"[0-9a-fA-F-]{36}(-(?:original|thumbnail)\.png|\.(?:xlsx|csv))", key):
        raise ValueError("Invalid legacy key")
    root = Path(directory).absolute()
    file = root / key
    if root.is_symlink() or file.is_symlink():
        raise ValueError("Symlink source is not allowed")
    if not file.is_file():
        return None
    if file.stat().st_size > 64 * 1024 * 1024:
        raise ValueError("Source exceeds size limit")
    return file.read_bytes()


def sources(db):
    for table, category, directory in (
        ("catalog_image_asset", "catalog-images", os.environ["ERP_CATALOG_IMAGE_DIR"]),
        ("dashboard_image_asset", "dashboard-images", os.environ["ERP_DASHBOARD_IMAGE_DIR"]),
    ):
        with db.cursor() as cursor:
            cursor.execute("SELECT original_storage_key,thumbnail_storage_key FROM " + table)
            for row in cursor:
                for key in row:
                    yield category, key, safe_file(directory, key)
    with db.cursor() as cursor:
        cursor.execute("SELECT storage_key,result_storage_key FROM import_batch")
        for row in cursor:
            for key in row:
                if key:
                    yield "imports", key, safe_file(os.environ["ERP_IMPORT_STAGING_DIR"], key)
    for table, category in (
        ("sku_document", "sku-documents"),
        ("dashboard_shipment_document", "shipment-documents"),
        ("carton_label_export", "carton-label-exports"),
    ):
        with db.cursor() as cursor:
            cursor.execute("SELECT id,content FROM " + table)
            for key, data in cursor:
                if not re.fullmatch(r"[0-9a-fA-F-]{36}", key):
                    raise ValueError("Invalid document key")
                yield category, key, bytes(data)


def copy_verified(bucket, key, data):
    import oss2
    expected = hashlib.sha256(data).digest()
    try:
        result = bucket.get_object(key)
    except oss2.exceptions.NoSuchKey:
        bucket.put_object(key, data, headers={
            "Content-Type": "application/octet-stream",
            "x-oss-object-acl": "private",
            "x-oss-forbid-overwrite": "true",
        })
        result = bucket.get_object(key)
    try:
        actual = hashlib.sha256()
        length = 0
        for block in result:
            length += len(block)
            if length > len(data):
                raise ValueError("Remote object size conflict")
            actual.update(block)
    finally:
        result.close()
    if actual.digest() != expected:
        raise ValueError("Remote content conflict; source retained")


def main():
    import oss2
    import pymysql
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="copy and verify without deleting sources")
    args = parser.parse_args()
    prefix = os.environ.get("ERP_OSS_PREFIX", "erp/")
    endpoint = os.environ["ERP_OSS_ENDPOINT"]
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9/_-]{0,100}/", prefix):
        raise ValueError("Invalid prefix")
    if not re.fullmatch(r"https://oss-[a-z0-9-]+\.aliyuncs\.com", endpoint):
        raise ValueError("Invalid endpoint")
    access_id, secret = credential_file(os.environ["ERP_OSS_CREDENTIALS_FILE"])
    bucket = oss2.Bucket(oss2.AuthV4(access_id, secret), endpoint,
                        os.environ["ERP_OSS_BUCKET"], region=os.environ["ERP_OSS_REGION"],
                        connect_timeout=30)
    password_file = Path(os.environ["ERP_MIGRATION_DB_PASSWORD_FILE"])
    if password_file.is_symlink() or password_file.stat().st_mode & 0o077:
        raise ValueError("Database credential permissions must be owner-only")
    # Restrict this initial migration tool to the existing local deployment.
    db = pymysql.connect(host="127.0.0.1",
                         port=int(os.environ.get("ERP_MIGRATION_DB_PORT", "13306")),
                         user=os.environ["ERP_MIGRATION_DB_USER"],
                         password=password_file.read_text().strip(),
                         database=os.environ["ERP_MIGRATION_DB_NAME"],
                         charset="utf8mb4", cursorclass=pymysql.cursors.SSCursor)
    count = missing = verified = 0
    try:
        # Preflight before writing any objects. Missing expired staging files are counted.
        for category, key, data in sources(db):
            count += 1
            if data is None:
                if category != "imports":
                    raise ValueError("Legacy image source missing; resolve before migration")
                missing += 1
        if args.apply:
            for category, key, data in sources(db):
                if data is not None:
                    copy_verified(bucket, prefix + category + "/" + key, data)
                    verified += 1
        print(f"mode={'apply' if args.apply else 'dry-run'} sources={count} missing_import_files={missing} verified={verified}")
    finally:
        db.close()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Never expose SDK responses, SQL rows, filenames, credentials or connection URLs.
        print("Migration failed; no source data deleted. Check configuration, access and source integrity.", file=sys.stderr)
        sys.exit(1)
