import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from release import unpack

SHA = 'a'*40
class ReleaseTest(unittest.TestCase):
    def archive(self, base, extra=None, bad_hash=False):
        files = {'backend.jar': b'jar', 'frontend/index.html': b'<html>', 'frontend/version.json': json.dumps({'revision':SHA}).encode()}
        manifest = {'revision':SHA, 'files':{n:hashlib.sha256(v).hexdigest() for n,v in files.items()}}
        if bad_hash: manifest['files']['backend.jar']='0'*64
        files['manifest.json']=json.dumps(manifest).encode()
        path=base/'release.tar.gz'
        with tarfile.open(path,'w:gz') as archive:
            for name,data in files.items():
                info=tarfile.TarInfo(name);info.size=len(data);archive.addfile(info,io.BytesIO(data))
            if extra:
                name,kind=extra
                info=tarfile.TarInfo(name);info.type=kind;info.linkname='/etc/passwd';archive.addfile(info,io.BytesIO())
        return path
    def test_valid(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);unpack(self.archive(p),p/'out',SHA)
            self.assertEqual((p/'out/backend.jar').read_bytes(),b'jar')
    def test_unsafe_members(self):
        for name,kind in [('../outside',tarfile.REGTYPE),('/absolute',tarfile.REGTYPE),('frontend/link',tarfile.SYMTYPE),('frontend/hardlink',tarfile.LNKTYPE),('backend.jar',tarfile.REGTYPE),('.env',tarfile.REGTYPE)]:
            with self.subTest(name=name),tempfile.TemporaryDirectory() as d:
                p=Path(d)
                with self.assertRaises(ValueError):unpack(self.archive(p,(name,kind)),p/'out',SHA)
                self.assertFalse((p/'out').exists())
    def test_checksum_and_revision(self):
        for bad_hash,revision in [(True,SHA),(False,'b'*40)]:
            with tempfile.TemporaryDirectory() as d:
                p=Path(d)
                with self.assertRaises(ValueError):unpack(self.archive(p,bad_hash=bad_hash),p/'out',revision)
                self.assertFalse((p/'out').exists())
    def test_refuses_existing_release(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);(p/'out').mkdir();(p/'out/keep').write_text('keep')
            with self.assertRaises(ValueError):unpack(self.archive(p),p/'out',SHA)
            self.assertEqual((p/'out/keep').read_text(),'keep')
if __name__=='__main__':unittest.main()
