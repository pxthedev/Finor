const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const loaded = { exports: {} };
const compiled = ts.transpileModule(
  fs.readFileSync(path.join(__dirname, "../src/installments.ts"), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS } },
).outputText;
vm.runInNewContext(compiled, { module: loaded, exports: loaded.exports, Date });
const { installmentDueDate, installmentProgress } = loaded.exports;
test("monthly due dates preserve the original day across short months", () => {
  assert.equal(installmentDueDate("2027-01-31", 1), "2027-02-28");
  assert.equal(installmentDueDate("2027-01-31", 2), "2027-03-31");
  assert.equal(installmentDueDate("2028-01-31", 1), "2028-02-29");
  assert.equal(installmentDueDate("2026-12-15", 1), "2027-01-15");
});
test("an existing purchase shows remaining installments and amount", () => {
  const result = installmentProgress({
    installment_amount: "125.50",
    total_installments: 10,
    paid_installments: 4,
    first_due_date: "2026-01-10",
  });
  assert.equal(result.remaining, 6);
  assert.equal(result.remainingAmount, 753);
  assert.equal(result.progress, 40);
  assert.equal(result.nextDueDate, "2026-05-10");
});
test("a paid purchase has no next due date or remaining debt", () => {
  const result = installmentProgress({
    installment_amount: "0.10",
    total_installments: 3,
    paid_installments: 3,
    first_due_date: "2026-01-10",
  });
  assert.equal(result.remainingAmount, 0);
  assert.equal(result.nextDueDate, null);
  assert.equal(result.progress, 100);
});
