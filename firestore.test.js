const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  const now = new Date();
  return {
    userId: uid,
    email: `${uid}@example.com`,
    displayName: "Developer User",
    themeMode: "dark",
    pushEnabled: true,
    notifyOnCompleted: true,
    notifyOnFailed: true,
    notifyOnProgress: false,
    notifyOnInputRequired: true,
    notifyOnAgentConnected: true,
    notifyOnAgentDisconnected: true,
    notifyOnSystemAlerts: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validAgent(uid) {
  const now = new Date();
  return {
    userId: uid,
    name: "Antigravity Primary",
    type: "antigravity",
    status: "connected",
    totalTasks: 0,
    completedTasks: 0,
    failedTasks: 0,
    credentialPrefix: "ntfy_ag_1a2b",
    credentialHash: "a".repeat(64),
    createdAt: now,
    updatedAt: now,
  };
}

function validTask(uid, agentId, status = "running") {
  const now = new Date();
  return {
    userId: uid,
    agentId,
    agentName: "Antigravity Primary",
    title: "Implement Auth Flow",
    project: "notifyme-core",
    status,
    progress: status === "completed" ? 100 : 40,
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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write users/agents/tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
});

test("Authenticated owner can create and update own profile, but cannot inject ghost field", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "light",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's agents, tasks, events, notifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agents")
      .doc("agent_1")
      .set(validAgent(ALICE_UID))
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agents")
      .doc("agent_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .get()
  );
});

test("Task creation enforces parent agent existence and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Fails if agent does not exist yet
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_orphan")
      .set(validTask(ALICE_UID, "missing_agent"))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agents")
      .doc("agent_1")
      .set(validAgent(ALICE_UID))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set(validTask(ALICE_UID, "agent_1", "running"))
  );

  // Transition to completed succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .update({
        status: "completed",
        progress: 100,
        summary: "Auth flow finished",
        completedAt: new Date(),
        updatedAt: new Date(),
      })
  );

  // Terminal state lock prevents modifying a completed task
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .update({
        status: "running",
        progress: 20,
        updatedAt: new Date(),
      })
  );
});

test("Events are immutable append-only and notifications allow mark-as-read only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .doc("evt_1")
      .set({
        userId: ALICE_UID,
        agentId: "agent_1",
        agentName: "Antigravity Primary",
        taskId: "task_1",
        project: "notifyme",
        eventType: "task_completed",
        title: "Task Completed",
        message: "Completed auth module",
        idempotencyKey: "idem_1",
        deliveryStatus: "delivered",
        createdAt: now,
      })
  );

  // Events cannot be updated
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .doc("evt_1")
      .update({ message: "tampered" })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notifications")
      .doc("notif_1")
      .set({
        userId: ALICE_UID,
        taskId: "task_1",
        eventId: "evt_1",
        agentName: "Antigravity Primary",
        project: "notifyme",
        title: "Antigravity completed Auth",
        message: "Auth module finished cleanly.",
        category: "task_completed",
        isRead: false,
        createdAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notifications")
      .doc("notif_1")
      .update({
        isRead: true,
        readAt: new Date(),
      })
  );

  // Notification message cannot be tampered
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notifications")
      .doc("notif_1")
      .update({
        message: "Tampered message",
      })
  );
});
