#!/usr/bin/env python3
"""Validate the ERP-006 active MVP scope overlay with the Python standard library."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = ROOT / "docs/01-product/mvp-active-scope-contract.json"
ACCESS_MATRIX_PATH = ROOT / "docs/05-security/ACCESS_MATRIX.md"
HISTORICAL_CONTRACT_README = ROOT / "deliverables/backend/sprint-0/import-contract/README.md"

EXPECTED_IMPORT_TYPES = {
    "PRODUCTS",
    "VARIANTS",
    "AUXILIARY_MATERIALS",
    "PRODUCT_AUXILIARY_MATERIALS",
    "AUXILIARY_MATERIAL_OPENING_INVENTORY",
}
FORBIDDEN_CAPABILITIES = {"listings.read", "listings.write", "stores.write"}
FORBIDDEN_API_RESOURCES = {"/api/v1/store-accounts", "/api/v1/store-listings"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
import_types = [item["id"] for item in contract["importTypes"]]
capabilities = contract["capabilities"]
api_resources = contract["apiResourceFamilies"]
excluded = contract["excludedCurrentMvp"]

require(contract["contractVersion"] == "1.2", "active contract must be ERP-029 scope version 1.2")
require(contract["decision"] == "DEC-012", "active contract must cite DEC-012")
require(set(import_types) == EXPECTED_IMPORT_TYPES, "active import types must be the five approved types")
require(len(import_types) == len(set(import_types)), "import types must be unique")
require("STORE_LISTINGS" in excluded["importTypes"], "STORE_LISTINGS must remain explicitly excluded")
versions = {item["id"]: item["templateVersion"] for item in contract["importTypes"]}
require(versions["PRODUCTS"] == versions["VARIANTS"] == "2.0", "catalog templates must be version 2.0")
require(all(versions[item] == "1.0" for item in EXPECTED_IMPORT_TYPES - {"PRODUCTS", "VARIANTS"}), "non-catalog templates must remain version 1.0")

require(len(capabilities) == 27, "active capability count must be 27")
require(len(capabilities) == len(set(capabilities)), "capabilities must be unique")
require(FORBIDDEN_CAPABILITIES.isdisjoint(capabilities), "Listing/store capabilities must be absent")
require(FORBIDDEN_CAPABILITIES.issubset(set(excluded["capabilities"])), "excluded capabilities must be explicit")

require(len(api_resources) == 16, "active API resource family count must be 16")
require(len(api_resources) == len(set(api_resources)), "API resource families must be unique")
require(FORBIDDEN_API_RESOURCES.isdisjoint(api_resources), "StoreAccount/StoreListing APIs must be absent")

require(set(contract["roleCapabilities"]) == set(contract["roles"]), "every role must have one capability mapping")
for role, role_capabilities in contract["roleCapabilities"].items():
    require(len(role_capabilities) == len(set(role_capabilities)), f"{role} capabilities must be unique")
    require(set(role_capabilities).issubset(set(capabilities)), f"{role} contains an unknown capability")
require(set(contract["roleCapabilities"]["ADMIN"]) == set(capabilities), "ADMIN must receive all active capabilities")

access_matrix = ACCESS_MATRIX_PATH.read_text(encoding="utf-8")
table_capabilities = set(re.findall(r"^\| `([^`]+)` \|", access_matrix, flags=re.MULTILINE))
require(table_capabilities == set(capabilities), "ACCESS_MATRIX capability rows must equal the machine contract")

historical_readme = HISTORICAL_CONTRACT_README.read_text(encoding="utf-8")
require("历史基线说明" in historical_readme, "historical six-type contract must carry a scope warning")
require("MVP_ACTIVE_SCOPE_CONTRACT.md" in historical_readme, "historical contract must link to the active scope")

require({"suppliers.read", "suppliers.write"}.issubset(capabilities), "supplier capabilities must be active")
require({"/api/v1/suppliers", "/api/v1/catalog-images"}.issubset(api_resources), "catalog API families must be active")
require(set(contract["roleCapabilities"]["SELECTION"]).isdisjoint({"suppliers.read", "suppliers.write"}), "SELECTION must not receive supplier access")
require({"purchases.read", "purchases.write", "purchases.receive"}.issubset(capabilities), "purchase capabilities must be active")
require("/api/v1/purchases" in api_resources, "purchase API family must be active")
require(set(contract["roleCapabilities"]["SELECTION"]).isdisjoint({"purchases.read", "purchases.write", "purchases.receive"}), "SELECTION must not receive purchase access")
print("PASS: 5 import types, 27 capabilities, 3 role mappings, 16 API resource families")
