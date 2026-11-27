import contextlib
import hashlib
import io
import os
from pathlib import Path
import subprocess
import shlex
import tempfile
import unittest
from unittest.mock import patch

from staged_upload import MAX_SIZE, run, validate
from upload_release import upload
from verify_retry_source import require_current_application


class StagedUploadTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.root = Path(self.work.name) / 'staging'
        self.data = b'verified release bytes'
        self.base = ['a' * 40, hashlib.sha256(self.data).hexdigest(), str(len(self.data))]

    def invoke(self, args, data=None, activation=False):
        if args == ['stage-current']:
            return 'c' * 40 + '\n'
        with tempfile.TemporaryFile() as incoming, io.StringIO() as output:
            incoming.write(data or b'')
            incoming.seek(0)
            with contextlib.redirect_stdout(output):
                run(args, root=self.root, fd=incoming.fileno(), activate=self.activate)
            return output.getvalue()

    def activate(self, path, revision, digest, expected):
        self.assertEqual(path.read_bytes(), self.data)
        self.assertEqual((revision, digest), tuple(self.base[:2]))

    def chunk(self, offset, data, digest=None):
        return self.invoke(['stage-chunk', *self.base, str(offset), str(len(data)),
                            digest or hashlib.sha256(data).hexdigest()], data)

    def test_resume_after_connection_loss_and_activate(self):
        self.assertEqual(self.invoke(['stage-status', *self.base]), '0\n')
        self.chunk(0, self.data[:8])
        self.assertEqual(self.invoke(['stage-status', *self.base]), '8\n')
        self.chunk(8, self.data[8:])
        self.invoke(['stage-activate', *self.base, 'c' * 40])
        self.assertFalse(list(self.root.glob('*.part')))

    def test_corrupt_truncated_and_duplicate_chunks_never_change_prefix(self):
        self.invoke(['stage-status', *self.base])
        self.chunk(0, self.data[:8])
        with self.assertRaisesRegex(ValueError, 'Offset changed'):
            self.chunk(0, self.data[:8])
        with self.assertRaisesRegex(ValueError, 'corrupt'):
            self.chunk(8, self.data[8:], 'b' * 64)
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            self.invoke(['stage-chunk', *self.base, '8', '4', 'b' * 64], b'x')
        self.assertEqual(self.invoke(['stage-status', *self.base]), '8\n')
        with self.assertRaisesRegex(ValueError, 'verification'):
            self.invoke(['stage-activate', *self.base, 'c' * 40])

    def test_failed_activation_keeps_archive(self):
        self.invoke(['stage-status', *self.base])
        self.chunk(0, self.data)
        self.activate = lambda *_: (_ for _ in ()).throw(RuntimeError('deployment failed'))
        with self.assertRaises(RuntimeError):
            self.invoke(['stage-activate', *self.base, 'c' * 40])
        self.assertEqual(len(list(self.root.glob('*.part'))), 1)

    def test_disk_quota_and_argument_restrictions(self):
        for args in (['stage-status', '../bad', *self.base[1:]],
                     ['stage-status', *self.base[:2], str(MAX_SIZE + 1)],
                     ['stage-chunk', *self.base, '0', '9999999', 'b' * 64],
                     ['stage-status', *self.base, 'extra']):
            with self.assertRaises(ValueError):
                validate(args)
        for i in range(4):
            self.invoke(['stage-status', str(i) * 40, *self.base[1:]])
        with self.assertRaisesRegex(ValueError, 'quota'):
            self.invoke(['stage-status', *self.base])

    def test_client_lost_ack_resumes_and_does_not_repeat_activation(self):
        path = Path(self.work.name) / 'release'
        path.write_bytes(self.data)
        lost = False
        activations = 0
        def connection(args, data, activation=False):
            nonlocal lost, activations
            result = self.invoke(args, data, activation)
            if args[0] == 'stage-chunk' and not lost:
                lost = True
                raise subprocess.CalledProcessError(255, 'ssh')
            if activation:
                activations += 1
                raise subprocess.CalledProcessError(255, 'ssh')
            return result
        with self.assertRaises(subprocess.CalledProcessError):
            upload(path, self.base[0], connection, sleep=lambda _: None)
        self.assertEqual(activations, 1)

    def test_same_upload_is_locked_during_activation(self):
        self.invoke(['stage-status', *self.base])
        self.chunk(0, self.data)
        def activate(*_):
            with self.assertRaises(BlockingIOError):
                self.invoke(['stage-status', *self.base])
        self.activate = activate
        self.invoke(['stage-activate', *self.base, 'c' * 40])

    def test_forced_command_rejects_shell_and_wrong_argument_shapes(self):
        entry = Path(__file__).with_name('ssh-entry.sh')
        for command in ('bash', 'stage-status ../../etc bad 1',
                        'stage-status ' + ' '.join(self.base) + '; id',
                        'stage-chunk ' + ' '.join(self.base) + ' 0 1 ' + 'b'*64 + ' extra'):
            result = subprocess.run(['bash', str(entry)], env={**os.environ, 'SSH_ORIGINAL_COMMAND': command},
                                    capture_output=True)
            self.assertEqual(result.returncode, 2)

    def test_activation_rejects_current_revision_changed_during_transfer(self):
        live = Path(self.work.name) / ('b' * 40)
        live.mkdir()
        (Path(self.work.name) / 'current').symlink_to(live)
        source = Path(__file__).with_name('receive.sh').read_text()
        guard = source[source.index('if [[ $# == 3'):source.index('# This marker')]
        prefix = 'set -eu\nroot=' + shlex.quote(self.work.name) + '\n'
        result = subprocess.run(['bash', '-c', prefix + guard, 'receiver',
                                 'a' * 40, 'd' * 64, 'c' * 40], capture_output=True)
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'Current revision changed', result.stderr)
        self.assertEqual((Path(self.work.name) / 'current').resolve(), live.resolve())

    def test_old_failed_application_cannot_replace_newer_application(self):
        repo = Path(self.work.name) / 'repo'
        repo.mkdir()
        def git(*args):
            return subprocess.check_output(['git', *args], cwd=repo, stderr=subprocess.DEVNULL).decode().strip()
        git('init', '-b', 'main')
        git('config', 'user.name', 'Deployment test')
        git('config', 'user.email', 'deployment-test@example.invalid')
        (repo/'frontend').mkdir()
        (repo/'frontend/app').write_text('A')
        git('add', '.')
        git('commit', '-m', 'application A')
        old = git('rev-parse', 'HEAD')
        (repo/'README.md').write_text('Documentation update')
        git('add', '.')
        git('commit', '-m', 'docs')
        require_current_application(old, 'main', cwd=repo)
        (repo/'frontend/app').write_text('B')
        git('add', '.')
        git('commit', '-m', 'application B')
        with self.assertRaisesRegex(ValueError, 'source changed'):
            require_current_application(old, 'main', cwd=repo)


if __name__ == '__main__':
    unittest.main()
