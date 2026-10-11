/**
 * Goblin warlord — art-directed voxel (ModelEngine / Blockbench 5).
 * Bez losowego szumu; każdy klocek ma sens w sylwetce.
 */
import { randomUUID } from "crypto";
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(__dirname, "..", "ModelEngine", "blueprints", "goblin");
mkdirSync(OUT_DIR, { recursive: true });

const TEX = 512;
const uid = () => randomUUID();

function boxFaces(uo, vo, w, h, d) {
  const x = uo;
  const y = vo;
  return {
    north: { uv: [x + d, y + d, x + d + w, y + d + h], texture: 0 },
    east: { uv: [x, y + d, x + d, y + d + h], texture: 0 },
    south: { uv: [x + d + w + d, y + d, x + d + w + d + w, y + d + h], texture: 0 },
    west: { uv: [x + d + w, y + d, x + d + w + d, y + d + h], texture: 0 },
    up: { uv: [x + d, y, x + d + w, y + d], texture: 0 },
    down: { uv: [x + d + w, y, x + d + w + w, y + d], texture: 0 },
  };
}

function cube(name, fr, to, origin, uvOff, opts = {}) {
  const { color = 4, visibility = true, rotation = null, inflate = 0 } = opts;
  const iw = Math.max(Math.round(to[0] - fr[0]), 1);
  const ih = Math.max(Math.round(to[1] - fr[1]), 1);
  const id = Math.max(Math.round(to[2] - fr[2]), 1);
  const el = {
    name,
    box_uv: true,
    render_order: "default",
    locked: false,
    allow_mirror_modeling: true,
    from: [...fr],
    to: [...to],
    autouv: 0,
    color,
    origin: [...origin],
    uv_offset: [...uvOff],
    faces: boxFaces(uvOff[0], uvOff[1], iw, ih, id),
    type: "cube",
    uuid: uid(),
  };
  if (!visibility) el.visibility = false;
  if (rotation) el.rotation = [...rotation];
  if (inflate) el.inflate = inflate;
  return el;
}

/** Strefy na atlasie 512×512 (goblin.png). */
const U = {
  skin: [24, 24],
  skinHi: [88, 24],
  skinLo: [152, 24],
  leather: [216, 24],
  leatherW: [280, 24],
  metal: [344, 24],
  cloth: [408, 24],
  wood: [24, 120],
  woodDark: [88, 120],
  eye: [464, 24],
  fang: [464, 56],
  rag: [152, 120],
};

const G = {
  head: [],
  body: [],
  left_arm: [],
  right_arm: [],
  left_leg: [],
  right_leg: [],
  back: [],
  hitbox: [],
  club: [],
};
const push = (g, ...c) => G[g].push(...c);

push("hitbox", cube("hitbox", [-5, 0, -5], [5, 31, 5], [0, 15.5, 0], [400, 400], { visibility: false, color: 3 }));

// ========== NOGI (krótkie, zgarbione, ciężkie buty) ==========
function buildLeg(side) {
  const g = side < 0 ? "left_leg" : "right_leg";
  const ox = side * 2.5;
  push(
    g,
    cube("hip", [ox - 1.5, 11.5, -1.8], [ox + 1.5, 13.5, 1.8], [ox, 12.5, 0], U.leather),
    cube("thigh", [ox - 1.4, 7.5, -1.7], [ox + 1.4, 11.5, 1.7], [ox, 9.5, 0], U.skinLo),
    cube("knee_guard", [ox - 1.8, 6.2, -2], [ox + 1.8, 7.8, 1.2], [ox, 7, -0.4], U.metal),
    cube("shin", [ox - 1.3, 3.2, -1.6], [ox + 1.3, 6.2, 1.6], [ox, 4.7, 0], U.leatherW),
    cube("boot", [ox - 1.9, 0, -2.4], [ox + 1.9, 3.2, 2], [ox, 1.6, -0.2], U.leather),
    cube("boot_fold", [ox - 2.1, 2.8, -2.2], [ox + 2.1, 3.6, 2.2], [ox, 3.2, 0], U.leatherW),
    cube("claw_a", [ox - 1.1, 0, -3], [ox - 0.3, 0.7, -2.2], [ox - 0.7, 0.35, -2.6], U.metal),
    cube("claw_b", [ox - 0.35, 0, -3.1], [ox + 0.45, 0.7, -2.3], [ox + 0.05, 0.35, -2.7], U.metal),
    cube("claw_c", [ox + 0.25, 0, -3], [ox + 1.05, 0.7, -2.2], [ox + 0.65, 0.35, -2.6], U.metal)
  );
  for (let i = 0; i < 3; i++) {
    push(g, cube(`strap_${i}`, [ox - 1.85, 4 + i * 2, -1.7], [ox - 1.55, 5 + i * 2, 1.7], [ox - 1.7, 4.5 + i * 2, 0], U.cloth));
  }
}
buildLeg(-1);
buildLeg(1);

// ========== TUŁÓW (garb, pancerz, pas, sakiewki) ==========
push(
  "body",
  cube("pelvis", [-3.8, 11, -2.4], [3.8, 13, 2.4], [0, 12, 0], U.cloth),
  cube("abdomen", [-3.2, 13, -2.2], [3.2, 15.5, 2.2], [0, 14.2, 0], U.skin),
  cube("chest", [-4.2, 15.5, -2.8], [4.2, 21, 2.8], [0, 18.2, 0], U.leather, { inflate: 0.05 }),
  cube("chest_plate", [-2.8, 16.2, -3.05], [2.8, 19.8, -2.45], [0, 18, -2.75], U.metal),
  cube("rivet_l", [-2.2, 18, -3.08], [-1.6, 18.6, -2.9], [-1.9, 18.3, -2.99], U.metal),
  cube("rivet_r", [1.6, 18, -3.08], [2.2, 18.6, -2.9], [1.9, 18.3, -2.99], U.metal),
  cube("rivet_c", [-0.3, 17.2, -3.1], [0.3, 17.8, -2.92], [0, 17.5, -3.01], U.metal),
  cube("strap_diag_l", [-4.5, 14, -2.5], [-2.5, 20, 2.5], [-3.5, 17, 0], U.leatherW, { rotation: [0, 0, 28] }),
  cube("strap_diag_r", [2.5, 14, -2.5], [4.5, 20, 2.5], [3.5, 17, 0], U.leatherW, { rotation: [0, 0, -28] }),
  cube("belt", [-4.3, 12.8, -3], [4.3, 14, 3], [0, 13.4, 0], U.leather),
  cube("buckle", [-1.2, 12.9, -3.15], [1.2, 13.9, -2.55], [0, 13.4, -2.85], U.metal),
  cube("pouch_l", [-4.8, 12.5, -1.2], [-3.1, 14.5, 1.4], [-3.95, 13.5, 0.1], U.leather),
  cube("pouch_r", [3.1, 12.5, -1.2], [4.8, 14.5, 1.4], [3.95, 13.5, 0.1], U.leather),
  cube("shoulder_l", [-5.2, 19, -2.8], [-3, 21.2, 2.8], [-4.1, 20.1, 0], U.leather, { inflate: 0.08 }),
  cube("shoulder_r", [3, 19, -2.8], [5.2, 21.2, 2.8], [4.1, 20.1, 0], U.leather, { inflate: 0.08 }),
  cube("spike_sl", [-5.4, 20.5, -0.4], [-4.8, 21.8, 0.4], [-5.1, 21.1, 0], U.metal),
  cube("spike_sr", [4.8, 20.5, -0.4], [5.4, 21.8, 0.4], [5.1, 21.1, 0], U.metal)
);

// ========== PLECY (garb, łopatki, wiszące łachmany) ==========
push(
  "back",
  cube("hunch", [-3, 17.5, 2.2], [3, 21, 5.2], [0, 19.2, 3.7], U.skinLo),
  cube("spine_1", [-0.6, 18, 3.8], [0.6, 19.2, 4.8], [0, 18.6, 4.3], U.skinHi),
  cube("spine_2", [-0.5, 19.5, 4], [0.5, 20.5, 5], [0, 20, 4.5], U.skinHi),
  cube("spine_3", [-0.4, 20.8, 3.5], [0.4, 21.8, 4.5], [0, 21.3, 4], U.skinHi)
);
for (let i = 0; i < 9; i++) {
  const x = -3.6 + i * 0.85;
  const len = 5 + (i % 4) * 1.4;
  push("back", cube(`tail_rag_${i}`, [x, 11.5 - len, 2.4], [x + 0.65, 11.8, 3.15], [x + 0.32, 11.65, 2.78], U.rag));
}

// ========== GŁOWA (wielka, agresywna) ==========
const Y = 20.5;
push(
  "head",
  cube("cranium", [-4, Y, -3.4], [4, Y + 6, 3.2], [0, Y + 3, 0], U.skin, { inflate: 0.04 }),
  cube("skull_top", [-3.2, Y + 5.5, -2.8], [3.2, Y + 6.8, 2.5], [0, Y + 6.1, 0], U.skinHi),
  cube("brow_l", [-3.8, Y + 4.5, -3.6], [-0.4, Y + 5.3, -3.1], [-2.1, Y + 4.9, -3.35], U.skinLo),
  cube("brow_r", [0.4, Y + 4.5, -3.6], [3.8, Y + 5.3, -3.1], [2.1, Y + 4.9, -3.35], U.skinLo),
  cube("brow_horn_l", [-3.5, Y + 5.2, -3.3], [-2.4, Y + 6.2, -2.7], [-2.95, Y + 5.7, -3], U.skinLo, { rotation: [0, 0, -18] }),
  cube("brow_horn_r", [2.4, Y + 5.2, -3.3], [3.5, Y + 6.2, -2.7], [2.95, Y + 5.7, -3], U.skinLo, { rotation: [0, 0, 18] }),
  cube("socket_l", [-3.1, Y + 3.4, -3.5], [-1.5, Y + 4.6, -3.15], [-2.3, Y + 4, -3.32], U.skinLo),
  cube("socket_r", [1.5, Y + 3.4, -3.5], [3.1, Y + 4.6, -3.15], [2.3, Y + 4, -3.32], U.skinLo),
  cube("eye_l", [-2.85, Y + 3.55, -3.42], [-1.75, Y + 4.35, -3.18], [-2.3, Y + 3.95, -3.3], U.eye, { color: 5 }),
  cube("eye_r", [1.75, Y + 3.55, -3.42], [2.85, Y + 4.35, -3.18], [2.3, Y + 3.95, -3.3], U.eye, { color: 5 }),
  cube("nose_bridge", [-0.9, Y + 2.2, -4], [0.9, Y + 4.2, -3.35], [0, Y + 3.2, -3.68], U.skinHi),
  cube("nose_tip", [-1.3, Y + 1.4, -4.35], [1.3, Y + 2.6, -3.55], [0, Y + 2, -3.95], U.skin),
  cube("nostril_l", [-1.05, Y + 1.5, -4.38], [-0.15, Y + 2.1, -4.05], [-0.6, Y + 1.8, -4.22], U.skinLo),
  cube("nostril_r", [0.15, Y + 1.5, -4.38], [1.05, Y + 2.1, -4.05], [0.6, Y + 1.8, -4.22], U.skinLo),
  cube("upper_lip", [-2.8, Y + 0.8, -3.7], [2.8, Y + 1.6, -3], [0, Y + 1.2, -3.35], U.skinLo),
  cube("jaw", [-3.5, Y, -3.6], [3.5, Y + 1.4, -2.4], [0, Y + 0.7, -3], U.skin),
  cube("chin", [-2.2, Y, -3.9], [2.2, Y + 0.9, -3.2], [0, Y + 0.45, -3.55], U.skinLo)
);
for (let t = 0; t < 7; t++) {
  const tx = -2.4 + t * 0.75;
  push("head", cube(`tooth_${t}`, [tx, Y + 0.2, -3.75], [tx + 0.45, Y + 1.05, -3.35], [tx + 0.22, Y + 0.62, -3.55], U.fang));
}
push(
  "head",
  cube("ear_l_a", [-5.4, Y + 2.8, -1], [-3.4, Y + 4.8, 0.8], [-4.4, Y + 3.8, 0], U.skin),
  cube("ear_l_b", [-6.2, Y + 3.2, -0.4], [-4.6, Y + 5.8, 0.9], [-5.4, Y + 4.5, 0.25], U.skin, { rotation: [0, -22, 0] }),
  cube("ear_l_c", [-6.8, Y + 3.8, 0], [-5.5, Y + 6.2, 0.7], [-6.15, Y + 5, 0.35], U.skinHi, { rotation: [0, -32, 0] }),
  cube("ear_r_a", [3.4, Y + 2.8, -1], [5.4, Y + 4.8, 0.8], [4.4, Y + 3.8, 0], U.skin),
  cube("ear_r_b", [4.6, Y + 3.2, -0.4], [6.2, Y + 5.8, 0.9], [5.4, Y + 4.5, 0.25], U.skin, { rotation: [0, 22, 0] }),
  cube("ear_r_c", [5.5, Y + 3.8, 0], [6.8, Y + 6.2, 0.7], [6.15, Y + 5, 0.35], U.skinHi, { rotation: [0, 32, 0] }),
  cube("hood", [-4.2, Y + 5.8, -3.8], [4.2, Y + 7.2, 3], [0, Y + 6.5, 0], U.cloth, { inflate: 0.06 }),
  cube("hood_peak", [-0.5, Y + 7, -1], [0.5, Y + 8.2, 0.5], [0, Y + 7.6, -0.25], U.cloth),
  cube("earring_l", [-5.1, Y + 3.5, 0.5], [-4.7, Y + 4.1, 0.9], [-4.9, Y + 3.8, 0.7], U.metal),
  cube("earring_r", [4.7, Y + 3.5, 0.5], [5.1, Y + 4.1, 0.9], [4.9, Y + 3.8, 0.7], U.metal)
);

// ========== RĘCE ==========
function buildArm(side, withClub) {
  const g = side < 0 ? "left_arm" : "right_arm";
  const ox = side * 5;
  const xIn = ox - side * 1.3;
  const xOut = ox - side * 2.85;
  const x0 = Math.min(xIn, xOut);
  const x1 = Math.max(xIn, xOut);
  const tag = side < 0 ? "l" : "r";

  push(
    g,
    cube(`ua_${tag}`, [x0, 15.5, -1.7], [x1, 20.5, 1.7], [ox - side * 1.05, 18, 0], U.skin, { inflate: 0.03 }),
    cube(`ua_muscle_${tag}`, [x0 + 0.15, 16.5, -1.9], [x1 - 0.15, 19, 1.9], [ox - side * 1.05, 17.75, 0], U.skinHi),
    cube(`bracer_${tag}`, [x0 - side * 0.15, 15.8, -1.9], [x1 + side * 0.15, 17.2, 1.9], [ox - side * 1.05, 16.5, 0], U.leather),
    cube(`fa_${tag}`, [x0, 11.5, -1.6], [x1, 15.5, 1.6], [ox - side * 1.05, 13.5, 0], U.skinLo),
    cube(`wrap_${tag}`, [x0 + 0.1, 10.8, -1.5], [x1 - 0.1, 11.6, 1.5], [ox - side * 1.05, 11.2, 0], U.cloth),
    cube(`palm_${tag}`, [x0 + 0.15, 9.2, -1.3], [x1 - 0.15, 10.8, 1.3], [ox - side * 1.05, 10, 0], U.skin)
  );

  for (let f = 0; f < 4; f++) {
    const fx = ox - side * (0.45 + f * 0.32);
    push(
      g,
      cube(`ph_${tag}_${f}`, [fx - 0.22, 7.8, -1 + f * 0.15], [fx + 0.22, 9.2, 0.4 + f * 0.15], [fx, 8.5, 0], U.skin),
      cube(`pt_${tag}_${f}`, [fx - 0.18, 7, -0.95 + f * 0.15], [fx + 0.18, 7.85, 0.35 + f * 0.15], [fx, 7.42, 0], U.skinHi)
    );
  }

  if (withClub) {
    const cx = side > 0 ? x1 : x0;
    push("club", cube("handle", [cx + side * 0.1, 5.5, -0.65], [cx + side * 1.15, 11.2, 0.65], [cx + side * 0.62, 8.3, 0], U.woodDark));
    for (let w = 0; w < 5; w++) {
      push("club", cube(`wrap_${w}`, [cx + side * 0.15, 6.5 + w * 0.85, -0.75], [cx + side * 1.05, 7.1 + w * 0.85, 0.75], [cx + side * 0.6, 6.8 + w * 0.85, 0], U.leatherW));
    }
    push(
      "club",
      cube("head_core", [cx - side * 0.35, 10.5, -1.6], [cx + side * 2.4, 14.2, 1.6], [cx + side * 1.02, 12.3, 0], U.wood, { inflate: 0.05 })
    );
    for (let s = 0; s < 8; s++) {
      const ang = s * 45;
      const rad = (ang * Math.PI) / 180;
      const sx = cx + side * (1.2 + Math.cos(rad) * 0.9);
      const sy = 11.5 + Math.sin(rad) * 1.1;
      push("club", cube(`spike_${s}`, [Math.min(sx, sx + side * 0.45), sy - 0.35, -0.5], [Math.max(sx, sx + side * 0.45), sy + 0.85, 0.5], [sx + side * 0.22, sy + 0.25, 0], U.metal));
    }
  }
}
buildArm(-1, false);
buildArm(1, true);

// ========== DETAL (celowe „wypukłości” — nie random) ==========
for (let i = 0; i < 12; i++) {
  const a = (i / 12) * Math.PI * 2;
  const rx = Math.cos(a) * 3.2;
  const rz = Math.sin(a) * 2.6;
  push("head", cube(`wart_${i}`, [rx - 0.28, Y + 1.5 + (i % 3) * 0.4, rz - 3.2 - 0.28], [rx + 0.28, Y + 1.5 + (i % 3) * 0.4 + 0.35, rz - 3.2 + 0.28], [rx, Y + 1.7, rz - 3.2], i % 2 ? U.skinHi : U.skinLo));
}
for (let i = 0; i < 8; i++) {
  push("body", cube(`stud_${i}`, [-3.5 + i * 1, 15.8, -2.95], [-3.5 + i * 1 + 0.35, 16.15, -2.75], [-3.32 + i * 1, 15.97, -2.85], U.metal));
}
for (let i = 0; i < 6; i++) {
  push("head", cube(`hood_tear_${i}`, [-3 + i * 1.1, Y + 6.2, -2.5 + (i % 2)], [-2.2 + i * 1.1, Y + 6.9, -1.5 + (i % 2)], [-2.6 + i * 1.1, Y + 6.55, -2], U.rag));
}
for (let i = 0; i < 10; i++) {
  const side = i < 5 ? -1 : 1;
  const ox = side * 2.5;
  const row = i % 5;
  push(side < 0 ? "left_leg" : "right_leg", cube(`boot_nail_${i}`, [ox - side * 0.5, 0.5 + row * 0.45, -2.15], [ox - side * 0.15, 0.85 + row * 0.45, -1.85], [ox - side * 0.32, 0.67 + row * 0.45, -2], U.metal));
}

// ========== Hierarchia ==========
const groupUuids = {};
for (const k of Object.keys(G)) groupUuids[k] = uid();

const ids = {};
for (const [k, v] of Object.entries(G)) ids[k] = v.map((e) => e.uuid);
const elements = Object.values(G).flat();

const origins = {
  body: [0, 17, 0],
  head: [0, 24, 0],
  left_arm: [-5, 18, 0],
  right_arm: [5, 18, 0],
  left_leg: [-2.5, 12, 0],
  right_leg: [2.5, 12, 0],
  back: [0, 18, 3],
  hitbox: [0, 15.5, 0],
  club: [6.5, 10, 0],
};

const groups = Object.keys(G).map((name) => ({
  uuid: groupUuids[name],
  export: true,
  locked: false,
  origin: origins[name],
  rotation: [0, 0, 0],
  color: 0,
  name,
  children: [],
  reset: false,
  shade: true,
  mirror_uv: false,
  selected: false,
  visibility: name !== "hitbox",
  autouv: 0,
  isOpen: name === "body",
}));

const outliner = [
  {
    uuid: groupUuids.body,
    isOpen: true,
    children: [
      ...ids.body,
      { uuid: groupUuids.back, isOpen: false, children: ids.back },
      { uuid: groupUuids.head, isOpen: true, children: ids.head },
    ],
  },
  { uuid: groupUuids.left_arm, isOpen: true, children: ids.left_arm },
  {
    uuid: groupUuids.right_arm,
    isOpen: true,
    children: [...ids.right_arm, { uuid: groupUuids.club, isOpen: true, children: ids.club }],
  },
  { uuid: groupUuids.left_leg, isOpen: true, children: ids.left_leg },
  { uuid: groupUuids.right_leg, isOpen: true, children: ids.right_leg },
  { uuid: groupUuids.hitbox, isOpen: false, children: ids.hitbox },
];

const kf = (channel, time, value, interp = "catmullrom") => ({
  channel,
  data_points: [value],
  uuid: uid(),
  time,
  color: -1,
  interpolation: interp,
});

const model = {
  meta: { format_version: "5.0", model_format: "free", box_uv: true },
  name: "goblin",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  variable_placeholder_buttons: [],
  timeline_setups: [],
  unhandled_root_fields: {},
  resolution: { width: TEX, height: TEX },
  elements,
  groups,
  outliner,
  textures: [
    {
      name: "goblin.png",
      relative_path: "goblin.png",
      folder: "block",
      namespace: "",
      id: "0",
      group: "",
      width: TEX,
      height: TEX,
      uv_width: TEX,
      uv_height: TEX,
      particle: false,
      use_as_default: false,
      layers_enabled: false,
      sync_to_project: "",
      render_mode: "default",
      render_sides: "auto",
      pbr_channel: "color",
      frame_time: 1,
      frame_order_type: "loop",
      frame_order: "",
      frame_interpolate: false,
      visible: true,
      internal: false,
      saved: true,
      uuid: uid(),
      path: "goblin.png",
    },
  ],
  animations: [
    {
      uuid: uid(),
      name: "idle",
      loop: "loop",
      override: false,
      length: 3,
      snapping: 20,
      selected: true,
      animators: {
        [groupUuids.head]: {
          name: "head",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "0", y: "0", z: "0" }),
            kf("rotation", 1.5, { x: "0", y: "-12", z: "0" }),
            kf("rotation", 3, { x: "0", y: "0", z: "0" }),
          ],
        },
        [groupUuids.back]: {
          name: "back",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "5", y: "0", z: "0" }),
            kf("rotation", 1.5, { x: "9", y: "0", z: "0" }),
            kf("rotation", 3, { x: "5", y: "0", z: "0" }),
          ],
        },
        [groupUuids.club]: {
          name: "club",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "0", y: "0", z: "8" }),
            kf("rotation", 1.5, { x: "0", y: "0", z: "14" }),
            kf("rotation", 3, { x: "0", y: "0", z: "8" }),
          ],
        },
        [groupUuids.body]: {
          name: "body",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("position", 0, { x: "0", y: "0", z: "0" }),
            kf("position", 1.5, { x: "0", y: "-0.2", z: "0" }),
            kf("position", 3, { x: "0", y: "0", z: "0" }),
          ],
        },
      },
    },
    {
      uuid: uid(),
      name: "walk",
      loop: "loop",
      override: false,
      length: 0.8,
      snapping: 20,
      selected: false,
      animators: {
        [groupUuids.left_leg]: {
          name: "left_leg",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "-26", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.4, { x: "26", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.8, { x: "-26", y: "0", z: "0" }, "linear"),
          ],
        },
        [groupUuids.right_leg]: {
          name: "right_leg",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "26", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.4, { x: "-26", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.8, { x: "26", y: "0", z: "0" }, "linear"),
          ],
        },
        [groupUuids.left_arm]: {
          name: "left_arm",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "24", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.4, { x: "-24", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.8, { x: "24", y: "0", z: "0" }, "linear"),
          ],
        },
        [groupUuids.right_arm]: {
          name: "right_arm",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "-16", y: "0", z: "10" }, "linear"),
            kf("rotation", 0.4, { x: "16", y: "0", z: "10" }, "linear"),
            kf("rotation", 0.8, { x: "-16", y: "0", z: "10" }, "linear"),
          ],
        },
      },
    },
    {
      uuid: uid(),
      name: "attack",
      loop: "once",
      override: false,
      length: 0.65,
      snapping: 20,
      selected: false,
      animators: {
        [groupUuids.right_arm]: {
          name: "right_arm",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "-40", y: "0", z: "20" }, "linear"),
            kf("rotation", 0.25, { x: "55", y: "0", z: "5" }, "linear"),
            kf("rotation", 0.65, { x: "-10", y: "0", z: "12" }, "linear"),
          ],
        },
        [groupUuids.club]: {
          name: "club",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "0", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.25, { x: "0", y: "0", z: "-35" }, "linear"),
            kf("rotation", 0.65, { x: "0", y: "0", z: "10" }, "linear"),
          ],
        },
        [groupUuids.body]: {
          name: "body",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "0", y: "0", z: "0" }, "linear"),
            kf("rotation", 0.25, { x: "0", y: "-15", z: "0" }, "linear"),
            kf("rotation", 0.65, { x: "0", y: "0", z: "0" }, "linear"),
          ],
        },
      },
    },
  ],
};

writeFileSync(join(OUT_DIR, "goblin.bbmodel"), JSON.stringify(model));
console.log(`Goblin premium: ${elements.length} cubes (art-directed)`);
