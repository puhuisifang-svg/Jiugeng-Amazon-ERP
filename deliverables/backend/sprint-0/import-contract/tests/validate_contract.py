#!/usr/bin/env python3
"""Dependency-free structural and cross-contract validation for S0-CONTRACT-01/02."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def load(name: str) -> dict:
    with (ROOT / name).open(encoding="utf-8") as handle:
        return json.load(handle)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def resolve_pointer(document: object, pointer: str) -> object:
    require(pointer.startswith("#/"), f"only local refs are allowed: {pointer}")
    value = document
    for raw_part in pointer[2:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        require(isinstance(value, dict) and part in value, f"unresolved ref: {pointer}")
        value = value[part]
    return value


def walk_refs(value: object, document: dict) -> None:
    if isinstance(value, dict):
        if "$ref" in value:
            resolve_pointer(document, value["$ref"])
        for child in value.values():
            walk_refs(child, document)
    elif isinstance(value, list):
        for child in value:
            walk_refs(child, document)


def main() -> None:
    api = load("openapi.json")
    machine = load("import-batch-state-machine.json")
    dag = load("import-dependency-dag.json")
    capabilities = load("capabilities.json")
    commit_semantics = load("import-commit-semantics.json")

    require(api["openapi"] == "3.1.0", "OpenAPI must be 3.1.0")
    require(api.get("security") == [], "Sprint 0 must not imply runtime IAM is implemented")
    require(api.get("x-auth-implementation") == "DEFERRED_TO_LATER_SPRINT", "IAM deferral marker missing")
    walk_refs(api, api)

    import_types = set(api["components"]["schemas"]["ImportType"]["enum"])
    expected_types = {
        "PRODUCTS", "VARIANTS", "STORE_LISTINGS", "AUXILIARY_MATERIALS",
        "PRODUCT_AUXILIARY_MATERIALS", "AUXILIARY_MATERIAL_OPENING_INVENTORY",
    }
    require(import_types == expected_types, "exactly six frozen importType values are required")
    require(set(commit_semantics["importSemantics"]) == import_types, "commit semantics and import types differ")
    require(commit_semantics["transactionMode"] == "ATOMIC_BATCH" and not commit_semantics["partialSuccessAllowed"], "all imports must commit atomically")
    ordinary = {name for name, value in commit_semantics["importSemantics"].items() if value["writeMode"] == "IDEMPOTENT_UPSERT"}
    require(ordinary == expected_types - {"AUXILIARY_MATERIAL_OPENING_INVENTORY"}, "ordinary Upsert must apply to exactly the first five imports")
    opening = commit_semantics["importSemantics"]["AUXILIARY_MATERIAL_OPENING_INVENTORY"]
    require(opening["writeMode"] == "CREATE_ONCE_IDEMPOTENT_COMMAND" and opening["ordinaryUpsertForbidden"], "opening inventory must not use ordinary Upsert")
    require(opening["openingQuantity"] == {"minimum": 0, "exclusiveMinimum": False, "zeroCreatesOpeningTransaction": True}, "OPENING must allow zero and still create a transaction")
    require(opening["otherInventoryTransactionQuantity"] == {"exclusiveMinimum": 0}, "non-OPENING transaction quantity must be positive")
    require(opening["sameKeyDifferentPayload"] == "OPENING_INVENTORY_CONFLICT", "opening payload conflict code differs")
    api_opening = api["x-domain-invariants"]["openingInventory"]
    require(api_opening["ordinaryUpsertForbidden"] and api_opening["zeroQuantityCreatesImmutableOpening"], "OpenAPI opening invariant missing")

    statuses = set(api["components"]["schemas"]["BatchStatus"]["enum"])
    require(statuses == set(machine["states"]), "OpenAPI and state-machine statuses differ")
    require("PARTIALLY_SUCCEEDED" not in statuses and "PARTIALLY_SUCCEEDED" not in machine["states"], "partial success is forbidden")
    require(set(api["components"]["schemas"]["BatchPhase"]["enum"]) == set(machine["phases"]), "phase values differ")
    require(len(machine["states"]) == 7 and len(machine["phases"]) == 8, "expected seven statuses and eight phases")
    require({name for name, state in machine["states"].items() if state["terminal"]} == {"SUCCEEDED", "FAILED", "CANCELLED"}, "terminal states differ")
    require({name for name, state in machine["states"].items() if state["cancellable"]} == {"QUEUED", "VALIDATING", "VALIDATED"}, "cancellable states differ")
    require(machine["previewTtlSeconds"] == 86400, "preview TTL must be 24 hours")
    for transition in machine["transitions"]:
        require(transition["from"] in statuses and transition["to"] in statuses, "transition references unknown state")
        require(not machine["states"][transition["from"]]["terminal"], "terminal state has outbound transition")

    issue = api["components"]["schemas"]["ImportIssue"]
    required_issue_fields = {"code", "severity", "blocking", "scope", "sheet", "row", "field", "reason", "rawValue", "suggestion"}
    require(set(issue["required"]) == required_issue_fields, "ImportIssue fields differ from DEL-017")
    require(set(api["components"]["schemas"]["IssueSeverity"]["enum"]) == {"BLOCKER", "ERROR", "WARNING", "INFO"}, "issue severity differs")

    paths = api["paths"]
    expected_operations = {
        "uploadAndValidateImportBatch", "listImportBatches", "getImportBatch", "listImportIssues",
        "getImportPreview", "revalidateImportBatch", "commitImportBatch", "cancelImportBatch", "getImportResult",
        "downloadImportResult", "downloadImportTemplate",
    }
    operations = []
    for path, path_item in paths.items():
        require(not path.startswith("/api/v1/stores"), "StoreAccount management API is forbidden")
        for method, operation in path_item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            operations.append(operation)
    require({operation["operationId"] for operation in operations} == expected_operations, "API operation set differs")

    upload_response = paths["/api/v1/import-batches"]["post"]["responses"]["202"]["content"]["application/json"]["schema"]
    require(upload_response == {"$ref": "#/components/schemas/QueuedImportBatch"}, "upload 202 must use the queued-only response schema")
    queued_schema = api["components"]["schemas"]["QueuedImportBatch"]
    queued_statuses = [part.get("properties", {}).get("status", {}).get("const") for part in queued_schema["allOf"]]
    require("QUEUED" in queued_statuses, "upload 202 status must be const QUEUED")

    capability_set = set(capabilities["capabilities"])
    for operation in operations:
        require(operation.get("x-capability") in capability_set, f"undefined capability on {operation['operationId']}")
        declared_errors = operation.get("x-error-codes")
        require(isinstance(declared_errors, list) and declared_errors, f"operation errors missing on {operation['operationId']}")
        require(set(declared_errors) <= set(api["x-error-catalog"]), f"unknown operation error on {operation['operationId']}")
        responses = operation["responses"]
        require("500" in responses, f"technical failure response missing on {operation['operationId']}")
        for status, response in responses.items():
            resolved_response = resolve_pointer(api, response["$ref"]) if "$ref" in response else response
            require("X-Request-Id" in resolved_response.get("headers", {}), f"X-Request-Id missing on {operation['operationId']} {status}")
        for code in declared_errors:
            require(str(api["x-error-catalog"][code]["httpStatus"]) in responses, f"{code} status is unreachable on {operation['operationId']}")
        for status in (value for value in responses if not value.startswith("2")):
            require(any(str(api["x-error-catalog"][code]["httpStatus"]) == status for code in declared_errors), f"response {status} has no error code on {operation['operationId']}")
    require("stores.write" not in capability_set, "stores.write must not exist")
    require(set(capabilities["roles"]) == {"ADMIN", "PRODUCT_MATERIAL", "SELECTION"}, "role set differs")
    import_caps = {value for value in capability_set if value.startswith("imports.")}
    expected_import_caps = {"imports.read", "imports.template.download", "imports.validate", "imports.commit", "imports.cancel", "imports.result.download"}
    require(import_caps == expected_import_caps, "six import capabilities differ")
    require(import_caps <= set(capabilities["roleCapabilities"]["ADMIN"]), "ADMIN lacks import capability")
    require(import_caps <= set(capabilities["roleCapabilities"]["PRODUCT_MATERIAL"]), "PRODUCT_MATERIAL lacks import capability")
    require(import_caps.isdisjoint(capabilities["roleCapabilities"]["SELECTION"]), "SELECTION must have no import capability")
    for role, grants in capabilities["roleCapabilities"].items():
        require(len(grants) == len(set(grants)), f"duplicate capability grant for {role}")
        require(set(grants) <= capability_set, f"unknown capability grant for {role}")

    for path, operation in [
        ("/api/v1/import-batches", paths["/api/v1/import-batches"]["post"]),
        ("/api/v1/import-batches/{batchId}/revalidate", paths["/api/v1/import-batches/{batchId}/revalidate"]["post"]),
        ("/api/v1/import-batches/{batchId}/commit", paths["/api/v1/import-batches/{batchId}/commit"]["post"]),
        ("/api/v1/import-batches/{batchId}/cancel", paths["/api/v1/import-batches/{batchId}/cancel"]["post"]),
    ]:
        parameters = [resolve_pointer(api, item["$ref"]) if "$ref" in item else item for item in operation["parameters"]]
        require(any(item["name"] == "Idempotency-Key" and item["required"] for item in parameters), f"missing required Idempotency-Key: {path}")

    wire = api["x-wire-conventions"]
    require(wire["jsonSuccessEnvelope"] == "DIRECT_SCHEMA", "JSON success envelope must be explicit")
    require(wire["paginationEnvelope"] == {"itemsField": "items", "metadataField": "page", "cursorField": "page.nextCursor"}, "pagination envelope differs")
    require(wire["errorMediaType"] == "application/problem+json" and wire["errorEnvelopeSchema"] == "#/components/schemas/ApiError", "error envelope differs")
    require(wire["requiredResponseHeaders"] == ["X-Request-Id"], "required response headers differ")
    for page_name in ("ImportBatchPage", "ImportIssuePage", "ImportPreviewPage"):
        page_schema = api["components"]["schemas"][page_name]
        require("items" in page_schema["properties"] and page_schema["properties"]["page"] == {"$ref": "#/components/schemas/PageMeta"}, f"page envelope differs: {page_name}")
    page_meta = api["components"]["schemas"]["PageMeta"]
    require(set(page_meta["required"]) == {"limit", "nextCursor", "hasMore"}, "cursor page metadata differs")

    revalidation_path = "/api/v1/import-batches/{batchId}/revalidate"
    revalidation_operation = paths[revalidation_path]["post"]
    require(revalidation_operation["responses"]["202"]["content"]["application/json"]["schema"] == {"$ref": "#/components/schemas/RevalidationReceipt"}, "revalidation response differs")
    receipt = api["components"]["schemas"]["RevalidationReceipt"]
    require(receipt["properties"]["sourceStatus"].get("const") == "VALIDATED" and receipt["properties"]["sourcePreviewExpired"].get("const") is True, "expired source semantics differ")
    require(receipt["properties"]["newBatch"] == {"$ref": "#/components/schemas/QueuedImportBatch", "description": "A distinct batch whose revalidationOfBatchId equals sourceBatchId."}, "revalidation must create a queued successor")
    revalidation = commit_semantics["revalidation"]
    require(revalidation["endpoint"] == f"POST {revalidation_path}", "revalidation endpoint differs")
    require(revalidation["sourceStatusAfterRequest"] == "VALIDATED" and revalidation["successorStatus"] == "QUEUED", "revalidation must not rewind source status")
    require(revalidation["newBatchIdRequired"] and revalidation["businessWrites"] == "NONE", "revalidation successor isolation differs")
    require(revalidation["maximumDirectSuccessorsPerSource"] == 1 and revalidation["sameRequestReplay"] == "RETURN_EXISTING_SUCCESSOR", "revalidation idempotency differs")
    require(set(commit_semantics["idempotency"]["requiredOperations"]) == {"UPLOAD_AND_VALIDATE", "REVALIDATE", "COMMIT", "CANCEL"}, "idempotent write operations differ")

    nodes = {node["importType"]: node for node in dag["importNodes"]}
    require(set(nodes) == import_types, "DAG and OpenAPI import types differ")
    require(nodes["PRODUCT_AUXILIARY_MATERIALS"]["logicalResource"] == "product_auxiliary_materials", "relationship logical name differs")
    require(nodes["AUXILIARY_MATERIAL_OPENING_INVENTORY"]["logicalResource"] == "auxiliary_material_opening_inventory", "opening logical name differs")
    system_nodes = {node["id"]: node for node in dag["systemNodes"]}
    require(system_nodes["STORE_ACCOUNTS"]["cardinality"] == 5, "exactly five StoreAccount presets required")
    require(not system_nodes["STORE_ACCOUNTS"]["managedByBusinessApi"], "StoreAccount management must remain unavailable")
    require(system_nodes["WAREHOUSE"]["cardinality"] == 1, "exactly one system warehouse required")

    known_nodes = import_types | set(system_nodes)
    for node in nodes.values():
        require(set(node["dependsOn"]) <= known_nodes, f"unknown DAG dependency for {node['importType']}")
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(name: str) -> None:
        if name in system_nodes or name in visited:
            return
        require(name not in visiting, "dependency graph contains a cycle")
        visiting.add(name)
        for dependency in nodes[name]["dependsOn"]:
            visit(dependency)
        visiting.remove(name)
        visited.add(name)

    for name in nodes:
        visit(name)

    errors = api["x-error-catalog"]
    required_conflicts = {
        "IDEMPOTENCY_KEY_REUSED", "IMPORT_ALREADY_COMMITTED", "IMPORT_PREVIEW_EXPIRED",
        "IMPORT_PREVIEW_MISMATCH", "IMPORT_WARNINGS_NOT_ACCEPTED", "IMPORT_NOT_CANCELLABLE",
        "OPENING_INVENTORY_CONFLICT", "IMPORT_CONCURRENT_CHANGE",
    }
    require(required_conflicts <= set(errors), "transaction/idempotency/retry error codes missing")
    require(all(errors[code]["httpStatus"] == 409 for code in required_conflicts), "conflict errors must use HTTP 409")
    require(errors["IMPORT_CONCURRENT_CHANGE"]["retryable"], "concurrent change must be explicitly retryable")
    require(not errors["OPENING_INVENTORY_CONFLICT"]["retryable"], "opening payload conflict must not auto-retry")

    issue_conditions = {}
    for condition in issue["allOf"]:
        scope = condition.get("if", {}).get("properties", {}).get("scope", {}).get("const")
        if scope:
            issue_conditions[scope] = condition["then"]["properties"]
    require(set(issue_conditions) == {"BATCH", "SHEET", "ROW", "FIELD"}, "all issue scopes need machine locator constraints")
    require(issue_conditions["BATCH"] == {"sheet": {"type": "null"}, "row": {"type": "null"}, "field": {"type": "null"}}, "BATCH locator constraint differs")
    require(issue_conditions["SHEET"]["sheet"]["type"] == "string" and issue_conditions["SHEET"]["row"]["type"] == "null", "SHEET locator constraint differs")
    require(issue_conditions["ROW"]["sheet"]["type"] == "string" and issue_conditions["ROW"]["row"] == {"type": "integer", "minimum": 2}, "ROW locator constraint differs")
    require(issue_conditions["FIELD"]["field"]["type"] == "string", "FIELD locator constraint differs")

    scalar_variants = api["components"]["schemas"]["TransportFieldValue"]["oneOf"]
    scalar_by_kind = {variant["properties"]["kind"]["const"]: variant["properties"]["value"] for variant in scalar_variants}
    require(set(scalar_by_kind) == {"STRING", "DECIMAL", "INTEGER", "BOOLEAN", "DATE", "DATETIME", "NULL"}, "canonical transport scalar kinds differ")
    require(scalar_by_kind["DECIMAL"]["type"] == "string" and "pattern" in scalar_by_kind["DECIMAL"], "decimal wire value must be a non-scientific string")
    require(scalar_by_kind["INTEGER"]["type"] == "integer" and scalar_by_kind["BOOLEAN"]["type"] == "boolean", "native scalar encoding differs")
    require(len(api["components"]["schemas"]["BusinessKey"]["oneOf"]) == 7, "business keys must cover six imports plus variant attributes")
    require(api["components"]["schemas"]["FieldMap"]["additionalProperties"] == {"$ref": "#/components/schemas/TransportFieldValue"}, "before/after field map differs")
    preview_row = api["components"]["schemas"]["PreviewRow"]["properties"]
    require(preview_row["before"]["oneOf"][0] == {"$ref": "#/components/schemas/FieldMap"} and preview_row["after"]["oneOf"][0] == {"$ref": "#/components/schemas/FieldMap"}, "before/after schema differs")
    require(preview_row["impacts"]["items"] == {"$ref": "#/components/schemas/PreviewImpact"}, "impacts schema differs")
    require(api["components"]["schemas"]["PreviewImpact"]["properties"]["resource"] == {"$ref": "#/components/schemas/ImpactResource"}, "row impact resource must reuse enum")
    require(api["components"]["schemas"]["ImpactSummaryItem"]["properties"]["resource"] == {"$ref": "#/components/schemas/ImpactResource"}, "impact summary resource must reuse enum")

    print("PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent")
    print(f"CHECKS: importTypes={len(import_types)} statuses={len(statuses)} phases={len(machine['phases'])} operations={len(operations)} capabilities={len(capability_set)} errors={len(errors)}")


if __name__ == "__main__":
    main()
