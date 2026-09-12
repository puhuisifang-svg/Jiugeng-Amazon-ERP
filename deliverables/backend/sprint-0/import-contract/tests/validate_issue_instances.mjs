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
const requiredLocatorCoverage = [
  "import-issue-invalid-batch-locator.json",
  "import-issue-invalid-field-missing.json",
  "import-issue-invalid-field.json",
  "import-issue-invalid-row-before-data.json",
  "import-issue-invalid-row-empty-sheet.json",
  "import-issue-invalid-row-missing-sheet.json",
  "import-issue-invalid-row.json",
  "import-issue-invalid-sheet-empty.json",
  "import-issue-invalid-sheet-missing.json",
  "import-issue-invalid-sheet.json"
];

for (const requiredFixture of requiredLocatorCoverage) {
  if (!fixtures.includes(requiredFixture)) {
    throw new Error(`required locator coverage missing: ${requiredFixture}`);
  }
}

let validAccepted = 0;
let invalidRejected = 0;
for (const name of fixtures) {
  const data = JSON.parse(fs.readFileSync(path.join(fixtureDir, name), "utf8"));
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

console.log(`PASS: ImportIssue Draft 2020-12 instances valid=${validAccepted} invalid-rejected=${invalidRejected}`);
