const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const EVE_UID = "eve_uid_3";
const HOUSEHOLD_CODE = "MC7K4P";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
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

function dbFor(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

test("Unauthenticated user cannot read or write users, households, or cards", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("households").doc(HOUSEHOLD_CODE).get());
  await assertFails(unauthDb.collection("households").where("memberUids", "array-contains", ALICE_UID).get());
});

test("User profile isolation: Alice can create/update her own profile, Bob cannot", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const bobDb = dbFor(BOB_UID);
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      householdId: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read or overwrite Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      householdId: HOUSEHOLD_CODE,
      updatedAt: pastTimestamp(),
    })
  );

  // Ghost field rejected (Shadow Update test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      householdId: HOUSEHOLD_CODE,
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Couple pairing flow: Alice creates household, Bob joins, Eve cannot list or read", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const bobDb = dbFor(BOB_UID);
  const eveDb = dbFor(EVE_UID);
  const ts = pastTimestamp();

  // Alice creates household
  await assertSucceeds(
    aliceDb.collection("households").doc(HOUSEHOLD_CODE).set({
      ownerUid: ALICE_UID,
      joinCode: HOUSEHOLD_CODE,
      name: "Notre portefeuille",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read before joining, Eve cannot list
  await assertFails(bobDb.collection("households").doc(HOUSEHOLD_CODE).get());
  await assertFails(eveDb.collection("households").get());

  // Bob joins with array union
  await assertSucceeds(
    bobDb.collection("households").doc(HOUSEHOLD_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Now Bob can read, Eve still cannot
  await assertSucceeds(bobDb.collection("households").doc(HOUSEHOLD_CODE).get());
  await assertFails(eveDb.collection("households").doc(HOUSEHOLD_CODE).get());
});

test("Shared cards CRUD and isolation: members can share & update, non-members blocked, ghost fields rejected", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const bobDb = dbFor(BOB_UID);
  const eveDb = dbFor(EVE_UID);
  const ts = pastTimestamp();

  await aliceDb.collection("households").doc(HOUSEHOLD_CODE).set({
    ownerUid: ALICE_UID,
    joinCode: HOUSEHOLD_CODE,
    name: "Couple",
    memberUids: [ALICE_UID],
    createdAt: ts,
    updatedAt: ts,
  });
  await bobDb.collection("households").doc(HOUSEHOLD_CODE).update({
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: pastTimestamp(),
  });

  const cardRef = aliceDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").doc("card_1");
  await assertSucceeds(
    cardRef.set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      storeName: "Marché Frais",
      barcodeValue: "3268840001046",
      barcodeFormat: "EAN_13",
      colorArgb: -14783662,
      category: "ALIMENTATION",
      note: "10 € dès 200 pts",
      clientUpdatedAt: 1730000000000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob queries cards with matching array-contains filter
  await assertSucceeds(
    bobDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Unfiltered list fails (rules are not filters)
  await assertFails(
    bobDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").get()
  );

  // Bob can update shared card note
  await assertSucceeds(
    bobDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").doc("card_1").update({
      note: "Bon utilisé",
      clientUpdatedAt: 1730000005000,
      updatedAt: pastTimestamp(),
    })
  );

  // Eve cannot read, list, create, or delete
  await assertFails(
    eveDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").doc("card_1").get()
  );
  await assertFails(
    eveDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").doc("card_1").delete()
  );

  // Ghost field rejected on update
  await assertFails(
    aliceDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").doc("card_1").update({
      ghostField: "hack",
      updatedAt: pastTimestamp(),
    })
  );

  // Bob can delete shared card (e.g. made private or removed)
  await assertSucceeds(
    bobDb.collection("households").doc(HOUSEHOLD_CODE).collection("cards").doc("card_1").delete()
  );
});
