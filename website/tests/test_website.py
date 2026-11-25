"""Contracts for the static, ERP-independent site."""
import unittest
from html.parser import HTMLParser
from pathlib import Path
from xml.etree import ElementTree
from urllib.parse import urlsplit

SRC = Path(__file__).resolve().parents[1] / "src"


class References(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.targets = []
        self.resources = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            self.ids.append(attrs["id"])
        if tag == "a":
            self.targets.append(attrs["href"])
        if tag in ("script", "img") or (tag == "link" and attrs["rel"] != "canonical"):
            self.resources.append(attrs.get("src") or attrs["href"])


class WebsiteContract(unittest.TestCase):
    def test_navigation_and_assets_work_from_ip(self):
        page = References()
        page.feed((SRC / "index.html").read_text())
        self.assertEqual(len(page.ids), len(set(page.ids)))
        contacts = {target for target in page.targets if urlsplit(target).scheme}
        self.assertEqual({urlsplit(target).scheme for target in contacts}, {"mailto", "tel"})
        for target in set(page.targets) - contacts:
            self.assertTrue(target.startswith("#"), target)
            self.assertIn(target[1:], page.ids)
        for ref in page.resources:
            self.assertFalse(urlsplit(ref).netloc, ref)
            self.assertTrue((SRC / ref).is_file(), ref)

    def test_sitemap_only_advertises_corporate_home(self):
        root = ElementTree.parse(SRC / "sitemap.xml")
        urls = [node.text for node in root.findall(".//{*}loc")]
        self.assertEqual(urls, ["https://jiugengerp.cn/"])
