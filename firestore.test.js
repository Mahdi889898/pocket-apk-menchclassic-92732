const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_host_123";
const BOB_UID = "bob_player_456";
const EVE_UID = "eve_outsider_789";
const ROOM_CODE = "MNCH42";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

function makeRoomPayload(overrides = {}) {
  const ts = pastDate(5000);
  return {
    roomCode: ROOM_CODE,
    roomName: "میز منچ قدیمی",
    hostUid: ALICE_UID,
    publicLobby: true,
    status: "WAITING",
    lobbyKey: "PUBLIC_WAITING",
    memberUids: [ALICE_UID],
    seatUids: [ALICE_UID, "", "", ""],
    seatNames: ["علی", "صندلی آزاد", "صندلی آزاد", "صندلی آزاد"],
    seatBotFlags: [false, false, false, false],
    tokens: Array(16).fill(-1),
    currentTurnSeat: 0,
    diceValue: 0,
    turnPhase: "ROLL",
    consecutiveSixes: 0,
    winnerSeat: -1,
    lastEventText: "اتاق ساخته شد؛ منتظر بازیکنان...",
    voiceEnabled: true,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
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

test("Unauthenticated users cannot read or write users, rooms, or voice_packets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").where("lobbyKey", "==", "PUBLIC_WAITING").get());
  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload()));
  await assertFails(
    unauthDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("voice_packets")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
});

test("User profile: owner CRUD succeeds, cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "علی",
      wins: 0,
      gamesPlayed: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "استاد منچ",
      wins: 1,
      gamesPlayed: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Ghost field & immutable createdAt rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      wins: 99,
      updatedAt: pastDate(500),
    })
  );

  // Scoped list query succeeds; unfiltered list fails
  await assertSucceeds(aliceDb.collection("users").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Room lifecycle: create, public lobby list, join, start, turn play, terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  // Host creates room (no @DocumentId 'id' field)
  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload()));

  // Reject ghost field / @DocumentId 'id' on create
  await assertFails(
    aliceDb.collection("rooms").doc("MNCH99").set(
      makeRoomPayload({
        id: "MNCH99",
        roomCode: "MNCH99",
      })
    )
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection("rooms").doc("MNCH88").set(
      makeRoomPayload({
        roomCode: "MNCH88",
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Filtered lobby query succeeds; unfiltered list fails
  await assertSucceeds(bobDb.collection("rooms").where("lobbyKey", "==", "PUBLIC_WAITING").get());
  await assertSucceeds(aliceDb.collection("rooms").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(bobDb.collection("rooms").get());

  // Bob joins waiting room
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      seatUids: [ALICE_UID, BOB_UID, "", ""],
      seatNames: ["علی", "بابک", "صندلی آزاد", "صندلی آزاد"],
      seatBotFlags: [false, false, false, false],
      lastEventText: "بابک به میز پیوست",
      updatedAt: pastDate(1500),
    })
  );

  // Non-host (Bob) cannot start the game
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      status: "PLAYING",
      lobbyKey: "PLAYING",
      updatedAt: pastDate(1200),
    })
  );

  // Host starts game filling empty seats 2 & 3 with bots
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).update({
      status: "PLAYING",
      lobbyKey: "PLAYING",
      seatUids: [ALICE_UID, BOB_UID, "BOT_2", "BOT_3"],
      seatNames: ["علی", "بابک", "ربات زرد", "ربات سبز"],
      seatBotFlags: [false, false, true, true],
      currentTurnSeat: 0,
      diceValue: 0,
      turnPhase: "ROLL",
      consecutiveSixes: 0,
      lastEventText: "بازی آغاز شد! نوبت علی است.",
      updatedAt: pastDate(1000),
    })
  );

  // Outsider cannot read a PLAYING room
  await assertFails(eveDb.collection("rooms").doc(ROOM_CODE).get());

  // Bob cannot play on Alice's turn (seat 0)
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      diceValue: 6,
      turnPhase: "MOVE",
      updatedAt: pastDate(900),
    })
  );

  // Alice rolls dice on seat 0
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).update({
      diceValue: 6,
      turnPhase: "MOVE",
      consecutiveSixes: 1,
      lastEventText: "علی تاس ۶ آورد!",
      updatedAt: pastDate(800),
    })
  );

  // Alice wins and finishes match -> terminal state
  const winningTokens = [40, 41, 42, 43, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1];
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).update({
      tokens: winningTokens,
      winnerSeat: 0,
      status: "FINISHED",
      lobbyKey: "FINISHED",
      turnPhase: "FINISHED",
      diceValue: 0,
      lastEventText: "علی برنده بازی شد!",
      updatedAt: pastDate(600),
    })
  );

  // Terminal state lock: NO further updates permitted after FINISHED
  await assertFails(
    aliceDb.collection("rooms").doc(ROOM_CODE).update({
      lastEventText: "تغییر پس از پایان",
      updatedAt: pastDate(300),
    })
  );
});

test("Voice packets: members can broadcast and listen; outsiders and updates rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc(ROOM_CODE).set(
      makeRoomPayload({
        status: "PLAYING",
        lobbyKey: "PLAYING",
        memberUids: [ALICE_UID, BOB_UID],
        seatUids: [ALICE_UID, BOB_UID, "BOT_2", "BOT_3"],
        seatNames: ["علی", "بابک", "ربات زرد", "ربات سبز"],
        seatBotFlags: [false, false, true, true],
      })
    );
  });

  const packetRef = aliceDb
    .collection("rooms")
    .doc(ROOM_CODE)
    .collection("voice_packets")
    .doc("pkt_001");

  await assertSucceeds(
    packetRef.set({
      roomId: ROOM_CODE,
      senderUid: ALICE_UID,
      senderName: "علی",
      senderSeat: 0,
      memberUids: [ALICE_UID, BOB_UID],
      audioBase64: "f39/f39/f39/f39/f39/f39/",
      durationMs: 1200,
      sampleRate: 8000,
      createdAt: pastDate(1000),
    })
  );

  // Bob (room member) can query voice packets with memberUids filter
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("voice_packets")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Eve (outsider) cannot read or create voice packets
  await assertFails(
    eveDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("voice_packets")
      .doc("pkt_001")
      .get()
  );
  await assertFails(
    eveDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("voice_packets")
      .doc("pkt_eve")
      .set({
        roomId: ROOM_CODE,
        senderUid: EVE_UID,
        senderName: "مزاحم",
        senderSeat: 1,
        memberUids: [ALICE_UID, BOB_UID, EVE_UID],
        audioBase64: "f39/f39/f39/f39/",
        durationMs: 1000,
        sampleRate: 8000,
        createdAt: pastDate(500),
      })
  );

  // Voice packets are immutable
  await assertFails(
    packetRef.update({
      audioBase64: "AAAAAAAAAAAA",
    })
  );
});
