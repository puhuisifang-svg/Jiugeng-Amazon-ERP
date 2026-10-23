import importlib.util
from pathlib import Path
import tempfile
import unittest
import io
import oss2

spec = importlib.util.spec_from_file_location("migration", Path(__file__).with_name("migrate-oss-resources.py"))
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


class FakeBucket:
    def __init__(self, data=None):
        self.data = data
        self.writes = 0

    def get_object(self, key):
        if self.data is None:
            raise oss2.exceptions.NoSuchKey(404, {}, b"", {})
        return io.BytesIO(self.data)

    def put_object(self, key, data, headers):
        assert headers["x-oss-forbid-overwrite"] == "true"
        assert headers["x-oss-object-acl"] == "private"
        self.writes += 1
        self.data = data


class MigrationTest(unittest.TestCase):
    def test_copy_and_repeat_are_idempotent(self):
        bucket = FakeBucket()
        migration.copy_verified(bucket, "test", b"synthetic")
        migration.copy_verified(bucket, "test", b"synthetic")
        self.assertEqual(bucket.writes, 1)

    def test_conflict_does_not_overwrite(self):
        bucket = FakeBucket(b"different")
        with self.assertRaises(ValueError):
            migration.copy_verified(bucket, "test", b"synthetic")
        self.assertEqual(bucket.writes, 0)

    def test_path_traversal_rejected(self):
        with self.assertRaises(ValueError):
            migration.safe_file("/tmp", "../secret")

    def test_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "source"
            target.write_bytes(b"synthetic")
            key = "00000000-0000-0000-0000-000000000000-original.png"
            (Path(directory) / key).symlink_to(target)
            with self.assertRaises(ValueError):
                migration.safe_file(directory, key)

    def test_credentials_must_be_private(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "credential"
            file.write_text("accessKeyId synthetic\naccessKeySecret synthetic\n")
            file.chmod(0o644)
            with self.assertRaises(ValueError):
                migration.credential_file(file)
            file.chmod(0o600)
            self.assertEqual(migration.credential_file(file), ["synthetic", "synthetic"])


if __name__ == "__main__":
    unittest.main()
