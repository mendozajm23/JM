const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validEmployee(userId) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    fullName: "Carlos Mendoza",
    roleTitle: "Analista Contable",
    department: "Finanzas",
    employeeCode: "EMP-001",
    monthlyBaseSalary: 22000,
    payFrequency: "quincenal",
    dailyWorkHours: 8,
    overtimeMultiplier: 2.0,
    isSelf: true,
    active: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validAttendance(userId, employeeId) {
  const now = new Date(Date.now() - 2000);
  const clockIn = Date.now() - 8 * 3600 * 1000;
  const clockOut = Date.now();
  return {
    userId,
    employeeId,
    employeeName: "Carlos Mendoza",
    workDate: "2026-11-09",
    clockInMillis: clockIn,
    clockOutMillis: clockOut,
    modality: "presencial",
    status: "completado",
    regularHours: 8.0,
    overtimeHours: 0.0,
    notes: "Jornada normal",
    createdAt: now,
    updatedAt: now,
  };
}

function validPayroll(userId, employeeId) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    employeeId,
    employeeName: "Carlos Mendoza",
    periodType: "quincenal",
    periodLabel: "1ra Quincena Nov 2026",
    baseSalaryPeriod: 11000,
    overtimeHours: 0,
    overtimePay: 0,
    bonuses: 0,
    grossPay: 11000,
    inssEmployee: 770,
    taxableBasePeriod: 10230,
    projectedAnnualIncome: 245520,
    irBracketLabel: "20% sobre exceso de C$ 200,000",
    irPeriod: 1004.33,
    otherDeductions: 0,
    netPay: 9225.67,
    status: "borrador",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write employees, attendance_logs, or payrolls", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/employees`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/attendance_logs`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/payrolls`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/employees`).doc("emp1").set(validEmployee(ALICE_UID))
  );
});

test("Authenticated owner: can create and read their own employee profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/employees`).doc("self").set(validEmployee(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/employees`).get());
});

test("Cross-user isolation: Bob cannot read or write Alice's employees, logs, or payrolls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/employees`).doc("self").set(validEmployee(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/employees`).doc("self").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/employees`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/attendance_logs`).doc("log1").set(validAttendance(ALICE_UID, "self"))
  );
});

test("Orphaned record guard: cannot create attendance log or payroll for non-existent employee", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/attendance_logs`).doc("log1").set(validAttendance(ALICE_UID, "missing_emp"))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/payrolls`).doc("pay1").set(validPayroll(ALICE_UID, "missing_emp"))
  );
});

test("Shadow update & Ghost field test: rejects extra unknown fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badEmployee = { ...validEmployee(ALICE_UID), isAdmin: true };
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/employees`).doc("self").set(badEmployee)
  );
});

test("Attendance & Payroll lifecycle + Terminal state locking on 'pagado' payroll", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/employees`).doc("self").set(validEmployee(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/attendance_logs`).doc("log1").set(validAttendance(ALICE_UID, "self"))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/payrolls`).doc("pay1").set(validPayroll(ALICE_UID, "self"))
  );

  // Mark payroll as pagado (allowed transition from borrador)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/payrolls`).doc("pay1").update({
      status: "pagado",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Subsequent modification of a pagado payroll must fail (terminal state lock)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/payrolls`).doc("pay1").update({
      netPay: 99999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
