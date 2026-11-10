const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");

// The data loader is independent of React Native, so regressions can run in Node.
const source = fs.readFileSync(
  path.join(__dirname, "../src/planning-summary.ts"),
  "utf8",
);
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const loaded = { exports: {} };
vm.runInNewContext(compiled, {
  exports: loaded.exports,
  module: loaded,
  Intl,
  Date,
});
const { loadPlanningSummary, incomeDateFromInput } = loaded.exports;
const schedule = {
  monthly_amount: "3000",
  next_income: "2026-12-05",
  remaining_essentials: "0",
  safety_margin: "0",
};

test("new account does not request a calculation before planning exists", async () => {
  const calls = [];
  const result = await loadPlanningSummary(async (url) => {
    calls.push(url);
    return [];
  }, "2026-11-08");
  assert.deepEqual(calls, ["/planning/schedule"]);
  assert.equal(result.available, null);
  assert.equal(result.schedule, null);
  assert.equal(result.needsUpdate, false);
});

test("saved planning enables the calculation", async () => {
  const calls = [];
  const available = { available: 800, suggestedDaily: 20 };
  const result = await loadPlanningSummary(async (url) => {
    calls.push(url);
    return url.endsWith("schedule") ? [schedule] : available;
  }, "2026-11-08");
  assert.deepEqual(calls, ["/planning/schedule", "/planning/available"]);
  assert.equal(result.available, available);
  assert.equal(result.schedule, schedule);
});

test("past or current income date prompts an update without a failing calculation", async () => {
  for (const next_income of ["2026-11-07", "2026-11-08"]) {
    const calls = [];
    const result = await loadPlanningSummary(async (url) => {
      calls.push(url);
      return [{ ...schedule, next_income }];
    }, "2026-11-08");
    assert.deepEqual(calls, ["/planning/schedule"]);
    assert.equal(result.needsUpdate, true);
    assert.equal(result.available, null);
  }
});

test("real server failures remain visible rather than becoming an empty state", async () => {
  const failure = new Error("Sem conexão com o servidor.");
  await assert.rejects(
    loadPlanningSummary(async () => {
      throw failure;
    }),
    (error) => error === failure,
  );
  await assert.rejects(
    loadPlanningSummary(async (url) => {
      if (url.endsWith("schedule")) return [schedule];
      throw failure;
    }, "2026-11-08"),
    (error) => error === failure,
  );
});

test("simple Brazilian dates are converted and impossible dates rejected", () => {
  assert.equal(incomeDateFromInput("05/12/2026"), "2026-12-05");
  assert.equal(incomeDateFromInput("29/02/2028"), "2028-02-29");
  for (const value of [
    "31/02/2026",
    "29/02/2027",
    "32/12/2026",
    "05/13/2026",
    "",
    "05/12",
  ]) {
    assert.equal(incomeDateFromInput(value), null);
  }
});
