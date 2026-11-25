import os
from pathlib import Path
import tempfile
import threading
import unittest
import signal
import subprocess
import shlex

from receive_stream import receive


class ReceiveStreamTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / 'release.tar.gz'
        self.reader, self.writer = os.pipe()
        self.addCleanup(os.close, self.reader)
        self.messages = []

    def log(self, message, **_):
        self.messages.append(message)

    def test_complete_eof_and_bytes(self):
        os.write(self.writer, b'archive')
        os.close(self.writer)
        self.assertEqual(receive(self.reader, self.path, log=self.log), 7)
        self.assertEqual(self.path.read_bytes(), b'archive')
        self.assertTrue(self.messages[-1].startswith('Upload received: bytes=7'))

    def test_stalled_pipe_times_out_without_eof(self):
        self.addCleanup(os.close, self.writer)
        os.write(self.writer, b'partial')
        with self.assertRaisesRegex(TimeoutError, 'idle timeout: bytes=7'):
            receive(self.reader, self.path, idle=.15, total=2, interval=.03, log=self.log)
        self.assertTrue(any('Upload progress:' in line for line in self.messages))

    def test_continuous_trickle_still_has_total_deadline(self):
        stop = threading.Event()
        def send():
            while not stop.wait(.01):
                os.write(self.writer, b'x')
        thread = threading.Thread(target=send)
        thread.start()
        try:
            with self.assertRaisesRegex(TimeoutError, 'total timeout'):
                receive(self.reader, self.path, idle=1, total=.15, interval=.03, log=self.log)
        finally:
            stop.set()
            thread.join()
            os.close(self.writer)

    def test_rejects_oversized_stream(self):
        os.write(self.writer, b'too-large')
        os.close(self.writer)
        with self.assertRaisesRegex(ValueError, 'size limit exceeded'):
            receive(self.reader, self.path, limit=4, log=self.log)
        self.assertLessEqual(self.path.stat().st_size, 4)

    def test_receiver_term_reaps_child_and_removes_partial_directory(self):
        os.close(self.writer)
        scripts = Path(__file__).resolve().parent
        source = (scripts / 'receive.sh').read_text()
        # Exercise the actual pre-validation reception/cleanup block, without
        # root checks, services, production paths or a production lock.
        block = source[source.index('tmp=$(mktemp'):source.index("printf '%s  %s")]
        block = block.replace('/usr/local/lib/jiugeng/receive_stream.py',
                              str(scripts / 'receive_stream.py'))
        block = block.replace('receiver_pid=$!', 'receiver_pid=$!\necho CHILD:$receiver_pid')
        command = 'set -Eeuo pipefail\nroot=' + shlex.quote(self.directory.name) + '\n' + block
        process = subprocess.Popen(['bash', '-c', command], stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            child = None
            while child is None:
                line = process.stdout.readline()
                if not line:
                    self.fail('Receiver exited before starting reception')
                if line.startswith('CHILD:'):
                    child = int(line.split(':')[1])
            process.send_signal(signal.SIGTERM)
            process.wait(timeout=5)
            self.assertEqual(process.returncode, 143)
            with self.assertRaises(ProcessLookupError):
                os.kill(child, 0)
            self.assertFalse(list(Path(self.directory.name).glob('.release.*')))
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
            process.stdin.close()
            process.stdout.close()
            process.stderr.close()


if __name__ == '__main__':
    unittest.main()
