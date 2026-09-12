import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";

const testDir = path.dirname(fileURLToPath(import.meta.url));
const contract = JSON.parse(fs.readFileSync(path.join(testDir, "..", "openapi.json"), "utf8"));
const schemas = contract.components.schemas;

function rewriteRefs(value) {
  if (Array.isArray(value)) return value.map(rewriteRefs);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [
      key,
      key === "$ref" && typeof child === "string"
        ? child.replace("#/components/schemas/", "#/$defs/")
        : rewriteRefs(child)
    ]));
  }
  return value;
}

const transactionSchema = rewriteRefs({
  $schema: "https://json-schema.org/draft/2020-12/schema",
  ...schemas.InventoryTransactionQuantityRule,
  $defs: {
    NonNegativeDecimalString: schemas.NonNegativeDecimalString,
    PositiveDecimalString: schemas.PositiveDecimalString
  }
});

const validCases = [
  {transactionType: "OPENING", quantity: "0"},
  {transactionType: "OPENING", quantity: "0.0000"},
  {transactionType: "OPENING", quantity: "12.5000"},
  {transactionType: "INBOUND", quantity: "0.0001"},
  {transactionType: "OUTBOUND", quantity: "1"},
  {transactionType: "ADJUST_IN", quantity: "2.50"},
  {transactionType: "ADJUST_OUT", quantity: "3"},
  {transactionType: "REVERSAL", quantity: "4.0000"}
];

const invalidCases = [
  {transactionType: "OPENING", quantity: "-0.1"},
  {transactionType: "INBOUND", quantity: "0"},
  {transactionType: "OUTBOUND", quantity: "0.0000"},
  {transactionType: "ADJUST_IN", quantity: "-1"},
  {transactionType: "REVERSAL", quantity: 1},
  {transactionType: "UNKNOWN", quantity: "1"}
];

const ajv = new Ajv2020({allErrors: true, strict: true});
const validate = ajv.compile(transactionSchema);

for (const value of validCases) {
  if (!validate(value)) {
    throw new Error(`valid transaction rejected: ${JSON.stringify(value)}: ${ajv.errorsText(validate.errors)}`);
  }
}
for (const value of invalidCases) {
  if (validate(value)) {
    throw new Error(`invalid transaction accepted: ${JSON.stringify(value)}`);
  }
}

console.log(`PASS: InventoryTransaction quantity condition valid=${validCases.length} invalid-rejected=${invalidCases.length}`);
