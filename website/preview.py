"""Loopback-only development preview with the site's real 404 page."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

from build import DIST


class Preview(SimpleHTTPRequestHandler):
    def send_head(self):
        path = urlsplit(self.path).path
        if path != "/" and not (DIST / path.lstrip("/")).resolve().is_relative_to(DIST):
            self.send_error(404)
            return None
        return super().send_head()

    def list_directory(self, path):
        self.send_error(404)
        return None

    def send_error(self, code, message=None, explain=None):
        if code != 404:
            return super().send_error(code, message, explain)
        body = (DIST / "404.html").read_bytes()
        self.send_response(404)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=4188)
    args = parser.parse_args()
    if not (DIST / "website-version.json").is_file():
        parser.error("Run build.py first")
    with ThreadingHTTPServer(("127.0.0.1", args.port), partial(Preview, directory=str(DIST))) as server:
        print(f"http://127.0.0.1:{server.server_port}/", flush=True)
        server.serve_forever()
