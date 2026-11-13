const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  return {
    userId: uid,
    dailyGoalMl: 2500,
    preferredUnit: "ml",
    notificationsEnabled: true,
    reminderIntervalMinutes: 120,
    wakeHour: 8,
    sleepHour: 22,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function validWaterLog(uid) {
  return {
    userId: uid,
    amountMl: 250,
    drinkType: "water",
    dateKey: "2026-11-13",
    loggedAtMillis: 1763040000000,
    note: "Morning glass",
    createdAt: new Date(),
    updatedAt: new Date(),
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

test("Unauthenticated user: cannot read or write profiles or logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("water_logs").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("water_logs")
      .doc("log_1")
      .set(validWaterLog(ALICE_UID))
  );
});

test("Authenticated user: can create, read, and update own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      dailyGoalMl: 3000,
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: cannot list users collection or read/write another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set(validProfile(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set(validProfile(BOB_UID)));
});

test("Authenticated user: cannot inject ghost fields or invalid values into profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertFails(
    profileRef.set({
      ...validProfile(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    profileRef.set({
      ...validProfile(ALICE_UID),
      dailyGoalMl: 50,
    })
  );
});

test("Authenticated user: can create, list, update, and delete own water logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logsCol = aliceDb.collection("users").doc(ALICE_UID).collection("water_logs");
  const logRef = logsCol.doc("log_1");

  await assertSucceeds(logRef.set(validWaterLog(ALICE_UID)));
  await assertSucceeds(logsCol.get());
  await assertSucceeds(
    logRef.update({
      amountMl: 350,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(logRef.delete());
});

test("Authenticated user: cannot read, list, create, or delete another user's water logs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("water_logs")
      .doc("bob_log_1")
      .set(validWaterLog(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobLogsCol = aliceDb.collection("users").doc(BOB_UID).collection("water_logs");

  await assertFails(bobLogsCol.get());
  await assertFails(bobLogsCol.doc("bob_log_1").get());
  await assertFails(bobLogsCol.doc("alice_forged").set(validWaterLog(BOB_UID)));
  await assertFails(bobLogsCol.doc("bob_log_1").delete());
});

test("Authenticated user: rejects invalid water log fields (ghost field, out-of-bounds amount, bad dateKey)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logsCol = aliceDb.collection("users").doc(ALICE_UID).collection("water_logs");

  await assertFails(
    logsCol.doc("bad_ghost").set({
      ...validWaterLog(ALICE_UID),
      ghostField: "hack",
    })
  );
  await assertFails(
    logsCol.doc("bad_amount").set({
      ...validWaterLog(ALICE_UID),
      amountMl: 99999,
    })
  );
  await assertFails(
    logsCol.doc("bad_date").set({
      ...validWaterLog(ALICE_UID),
      dateKey: "11-13-2026",
    })
  );
  await assertFails(
    logsCol.doc("bad_drink").set({
      ...validWaterLog(ALICE_UID),
      drinkType: "soda",
    })
  );
});
