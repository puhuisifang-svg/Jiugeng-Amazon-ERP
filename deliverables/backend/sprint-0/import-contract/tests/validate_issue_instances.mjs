import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";

const testDir = path.dirname(fileURLToPath(import.meta.url));
const contract = JSON.parse(fs.readFileSync(path.join(testDir, "..", "openapi.json"), "utf8"));
const schemas = contract.components.schemas;

function rewriteRefs(value) {
  if (Array.isArray(value)) {
    return value.map(rewriteRefs);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => {
      if (key === "$ref" && typeof child === "string") {
        return [key, child.replace("#/components/schemas/", "#/$defs/")];
      }
      return [key, rewriteRefs(child)];
    }));
  }
  return value;
}

const issueSchema = rewriteRefs({
  $schema: "https://json-schema.org/draft/2020-12/schema",
  ...schemas.ImportIssue,
  $defs: {
    IssueSeverity: schemas.IssueSeverity,
    IssueScope: schemas.IssueScope
  }
});

const ajv = new Ajv2020({allErrors: true, strict: true});
const validate = ajv.compile(issueSchema);
const fixtureDir = path.join(testDir, "fixtures");
const fixtures = fs.readdirSync(fixtureDir).filter((name) => name.endsWith(".json")).sort();
const fixtureData = new Map(fixtures.map((name) => [
  name,
  JSON.parse(fs.readFileSync(path.join(fixtureDir, name), "utf8"))
]));
const hasOwn = (value, property) => Object.prototype.hasOwnProperty.call(value, property);
const requiredInvalidScenarios = new Map([
  ["import-issue-invalid-batch-locator.json", (data) => data.scope === "BATCH" && data.sheet !== null],
  ["import-issue-invalid-field-missing.json", (data) => data.scope === "FIELD" && !hasOwn(data, "field")],
  ["import-issue-invalid-field.json", (data) => data.scope === "FIELD" && hasOwn(data, "field") && data.field === null],
  ["import-issue-invalid-row-before-data.json", (data) => data.scope === "ROW" && data.sheet.length > 0 && data.row === 1],
  ["import-issue-invalid-row-empty-sheet.json", (data) => data.scope === "ROW" && data.sheet === "" && data.row === 2],
  ["import-issue-invalid-row-missing-sheet.json", (data) => data.scope === "ROW" && !hasOwn(data, "sheet") && data.row === 2],
  ["import-issue-invalid-row.json", (data) => data.scope === "ROW" && data.sheet.length > 0 && data.row === null],
  ["import-issue-invalid-sheet-empty.json", (data) => data.scope === "SHEET" && data.sheet === ""],
  ["import-issue-invalid-sheet-missing.json", (data) => data.scope === "SHEET" && !hasOwn(data, "sheet")],
  ["import-issue-invalid-sheet.json", (data) => data.scope === "SHEET" && hasOwn(data, "sheet") && data.sheet === null],
  ["import-issue-invalid-error-blocking.json", (data) => data.severity === "ERROR" && data.blocking === false],
  ["import-issue-invalid-warning-blocking.json", (data) => data.severity === "WARNING" && data.blocking === true]
]);

function requiredCoverageFailures(names, dataByName) {
  const failures = [];
  for (const [name, matchesScenario] of requiredInvalidScenarios) {
    if (!names.includes(name)) {
      failures.push(`missing:${name}`);
    } else if (!matchesScenario(dataByName.get(name))) {
      failures.push(`semantic-drift:${name}`);
    }
  }
  return failures;
}

const coverageFailures = requiredCoverageFailures(fixtures, fixtureData);
if (coverageFailures.length > 0) {
  throw new Error(`required invalid scenario coverage failed: ${coverageFailures.join(",")}`);
}

const requiredNames = [...requiredInvalidScenarios.keys()];
for (const [index, name] of requiredNames.entries()) {
  const withoutTarget = fixtures.filter((fixture) => fixture !== name);
  if (requiredCoverageFailures(withoutTarget, fixtureData).length === 0) {
    throw new Error(`deletion mutation was not detected: ${name}`);
  }

  const replacementName = requiredNames[(index + 1) % requiredNames.length];
  const replacedData = new Map(fixtureData);
  replacedData.set(name, fixtureData.get(replacementName));
  if (requiredCoverageFailures(fixtures, replacedData).length === 0) {
    throw new Error(`semantic replacement mutation was not detected: ${name} <- ${replacementName}`);
  }
}

let validAccepted = 0;
let invalidRejected = 0;
for (const name of fixtures) {
  const data = fixtureData.get(name);
  const accepted = validate(data);
  if (name.includes("-valid-")) {
    if (!accepted) {
      throw new Error(`valid fixture rejected: ${name}: ${ajv.errorsText(validate.errors)}`);
    }
    validAccepted += 1;
  } else if (name.includes("-invalid-")) {
    if (accepted) {
      throw new Error(`invalid fixture accepted: ${name}`);
    }
    invalidRejected += 1;
  } else {
    throw new Error(`fixture name must declare valid or invalid: ${name}`);
  }
}

if (validAccepted !== 4 || invalidRejected !== 12) {
  throw new Error(`unexpected fixture coverage: valid=${validAccepted} invalid=${invalidRejected}`);
}

console.log(`PASS: ImportIssue Draft 2020-12 instances valid=${validAccepted} invalid-rejected=${invalidRejected} required-scenarios=${requiredInvalidScenarios.size} deletion-mutations=${requiredInvalidScenarios.size} replacement-mutations=${requiredInvalidScenarios.size}`);
