import { randomUUID } from "crypto";
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(__dirname, "..", "ModelEngine", "blueprints", "darth_vader");
mkdirSync(OUT_DIR, { recursive: true });

const uid = () => randomUUID();

function boxFaces(uo, vo, w, h, d) {
  const x = uo,
    y = vo;
  return {
    north: { uv: [x + d, y + d, x + d + w, y + d + h], texture: 0 },
    east: { uv: [x, y + d, x + d, y + d + h], texture: 0 },
    south: { uv: [x + d + w, y + d, x + 2 * d + w, y + d + h], texture: 0 },
    west: { uv: [x + d + w, y + d, x + 2 * d + w, y + d + h], texture: 0 },
    up: { uv: [x + d, y, x + d + w, y + d], texture: 0 },
    down: { uv: [x + d + w, y, x + 2 * d + w, y + d], texture: 0 },
  };
}

function cube(name, fr, to, origin, uvOff = [0, 0], rotation = null, inflate = 0, visibility = true, color = 4) {
  let w = Math.round(to[0] - fr[0]);
  let h = Math.round(to[1] - fr[1]);
  let d = Math.round(to[2] - fr[2]);
  w = Math.max(w, 1);
  h = Math.max(h, 1);
  d = Math.max(d, 1);
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
    faces: boxFaces(uvOff[0], uvOff[1], w, h, d),
    type: "cube",
    uuid: uid(),
  };
  if (rotation) el.rotation = [...rotation];
  if (inflate) el.inflate = inflate;
  if (!visibility) el.visibility = false;
  return el;
}

const byGroup = {
  head: [],
  body: [],
  left_arm: [],
  right_arm: [],
  left_leg: [],
  right_leg: [],
  cape: [],
  collar: [],
  hitbox: [],
  lights: [],
};

byGroup.hitbox.push(cube("hitbox", [-5, 0, -5], [5, 36, 5], [0, 18, 0], [0, 56], null, 0, false, 3));

for (const [sx, gx] of [
  [-3.5, "left_leg"],
  [3.5, "right_leg"],
]) {
  byGroup[gx].push(cube("shin", [sx - 1.5, 0, -1.5], [sx + 1.5, 10, 1.5], [sx, 5, 0], [16, 80]));
  byGroup[gx].push(cube("boot_top", [sx - 2, 10, -2], [sx + 2, 14, 2], [sx, 12, 0], [32, 80]));
  byGroup[gx].push(cube("boot_toe", [sx - 2, 0, -3], [sx + 2, 4, -1.5], [sx, 2, -2], [48, 80]));
}

byGroup.body.push(cube("torso_main", [-5, 14, -3], [5, 24, 3], [0, 19, 0], [0, 0]));
byGroup.body.push(cube("torso_rib_l", [-5.5, 15, -2.5], [-4.5, 23, 2.5], [-5, 19, 0], [64, 0]));
byGroup.body.push(cube("torso_rib_r", [4.5, 15, -2.5], [5.5, 23, 2.5], [5, 19, 0], [64, 0]));
byGroup.body.push(cube("chest_panel", [-2.5, 17, -3.2], [2.5, 21, -2.8], [0, 19, -3], [80, 16]));
byGroup.body.push(cube("belt", [-5.5, 13, -3], [5.5, 15, 3], [0, 14, 0], [96, 0]));
byGroup.body.push(cube("belt_box_l", [-5.5, 13.2, -1], [-3.5, 14.8, 1], [-4.5, 14, 0], [96, 16]));
byGroup.body.push(cube("belt_box_r", [3.5, 13.2, -1], [5.5, 14.8, 1], [4.5, 14, 0], [96, 16]));
byGroup.body.push(cube("codpiece", [-1.5, 12, -1.5], [1.5, 14, 1.5], [0, 13, 0], [112, 0]));

byGroup.left_arm.push(cube("shoulder_l", [-8, 22, -3], [-4, 26, 3], [-6, 24, 0], [0, 32]));
byGroup.right_arm.push(cube("shoulder_r", [4, 22, -3], [8, 26, 3], [6, 24, 0], [0, 32]));
byGroup.left_arm.push(cube("arm_l", [-8, 14, -2], [-4, 22, 2], [-6, 18, 0], [16, 32]));
byGroup.right_arm.push(cube("arm_r", [4, 14, -2], [8, 22, 2], [6, 18, 0], [16, 32]));
byGroup.left_arm.push(cube("glove_l", [-8, 12, -2], [-4, 14, 2], [-6, 13, 0], [32, 32]));
byGroup.right_arm.push(cube("glove_r", [4, 12, -2], [8, 14, 2], [6, 13, 0], [32, 32]));

byGroup.collar.push(cube("collar", [-6, 23, -3], [6, 25, 3], [0, 24, 0], [48, 48]));

for (let i = 0, y = 24; y < 36; y += 3, i++) {
  const w = 9 + i * 0.3;
  byGroup.cape.push(cube(`cape_${i}`, [-w, y, 2.5], [w, y + 2.8, 2.55], [0, y + 1, 2.5], [64, 64]));
}

const headY = 24;
byGroup.head.push(cube("neck", [-2.5, headY, -2], [2.5, headY + 2, 2], [0, headY + 1, 0], [80, 48]));
byGroup.head.push(cube("helmet_core", [-5, headY + 2, -4], [5, headY + 12, 4], [0, headY + 7, 0], [0, 48]));
byGroup.head.push(cube("helmet_ridge", [-0.5, headY + 10, -4.5], [0.5, headY + 14, 4.5], [0, headY + 12, 0], [16, 48]));
byGroup.head.push(cube("face_mask", [-4.5, headY + 2, -4.5], [4.5, headY + 9, -3.5], [0, headY + 5, -4], [32, 48]));
byGroup.head.push(cube("mouth_grille", [-2, headY + 3, -4.6], [2, headY + 6, -4.2], [0, headY + 4.5, -4.4], [48, 48]));
byGroup.head.push(cube("cheek_l", [-6, headY + 4, -3], [-4.5, headY + 9, 2], [-5.2, headY + 6, 0], [64, 48], [0, 12, 0]));
byGroup.head.push(cube("cheek_r", [4.5, headY + 4, -3], [6, headY + 9, 2], [5.2, headY + 6, 0], [64, 48], [0, -12, 0]));
byGroup.head.push(cube("helmet_back", [-4, headY + 6, 3], [4, headY + 13, 7], [0, headY + 9, 5], [96, 48], [-18, 0, 0]));
byGroup.head.push(cube("eye_l", [-3.5, headY + 7, -4.55], [-1.5, headY + 8.5, -4.35], [-2.5, headY + 7.8, -4.5], [112, 48]));
byGroup.head.push(cube("eye_r", [1.5, headY + 7, -4.55], [3.5, headY + 8.5, -4.35], [2.5, headY + 7.8, -4.5], [112, 48]));

byGroup.lights.push(cube("light_chest_r", [1.2, 18.5, -3.25], [1.8, 19.1, -3.05], [1.5, 18.8, -3.15], [120, 8], null, 0, true, 6));
byGroup.lights.push(cube("light_chest_g", [0.2, 18.5, -3.25], [0.8, 19.1, -3.05], [0.5, 18.8, -3.15], [120, 12], null, 0, true, 5));
byGroup.lights.push(cube("light_belt_l", [-4.8, 13.8, 0.2], [-4.2, 14.2, 0.6], [-4.5, 14, 0.4], [120, 16], null, 0, true, 6));

const groupUuids = {};
for (const g of Object.keys(byGroup)) groupUuids[g] = uid();

const elementUuidsByGroup = {};
for (const [k, v] of Object.entries(byGroup)) elementUuidsByGroup[k] = v.map((e) => e.uuid);

const elements = Object.values(byGroup).flat();

const origins = {
  body: [0, 18, 0],
  head: [0, 28, 0],
  left_arm: [-6, 22, 0],
  right_arm: [6, 22, 0],
  left_leg: [-3.5, 12, 0],
  right_leg: [3.5, 12, 0],
  cape: [0, 28, 2.5],
  collar: [0, 24, 0],
  hitbox: [0, 18, 0],
  lights: [0, 18, -3],
};

const groups = Object.keys(byGroup).map((gname) => ({
  uuid: groupUuids[gname],
  export: true,
  locked: false,
  origin: origins[gname],
  rotation: [0, 0, 0],
  color: 0,
  name: gname,
  children: [],
  reset: false,
  shade: true,
  mirror_uv: false,
  selected: false,
  visibility: gname !== "hitbox",
  autouv: 0,
  isOpen: gname === "body",
}));

const outliner = [
  {
    uuid: groupUuids.body,
    isOpen: true,
    children: [
      { uuid: groupUuids.head, isOpen: false, children: elementUuidsByGroup.head },
      { uuid: groupUuids.collar, isOpen: false, children: elementUuidsByGroup.collar },
      { uuid: groupUuids.left_arm, isOpen: false, children: elementUuidsByGroup.left_arm },
      { uuid: groupUuids.right_arm, isOpen: false, children: elementUuidsByGroup.right_arm },
      { uuid: groupUuids.left_leg, isOpen: false, children: elementUuidsByGroup.left_leg },
      { uuid: groupUuids.right_leg, isOpen: false, children: elementUuidsByGroup.right_leg },
      { uuid: groupUuids.cape, isOpen: false, children: elementUuidsByGroup.cape },
      { uuid: groupUuids.lights, isOpen: false, children: elementUuidsByGroup.lights },
      ...elementUuidsByGroup.body,
    ],
  },
  { uuid: groupUuids.hitbox, isOpen: true, children: elementUuidsByGroup.hitbox },
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
  name: "darth_vader",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  variable_placeholder_buttons: [],
  timeline_setups: [],
  unhandled_root_fields: {},
  resolution: { width: 128, height: 128 },
  elements,
  groups,
  outliner,
  textures: [
    {
      name: "darth_vader.png",
      relative_path: "darth_vader.png",
      folder: "block",
      namespace: "",
      id: "0",
      group: "",
      width: 128,
      height: 128,
      uv_width: 128,
      uv_height: 128,
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
      path: "darth_vader.png",
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
      group_name: "",
      anim_time_update: "",
      blend_weight: "",
      start_delay: "",
      loop_delay: "",
      animators: {
        [groupUuids.body]: {
          name: "body",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("position", 0, { x: "0", y: "0", z: "0" }),
            kf("position", 1.5, { x: "0", y: "-0.35", z: "0" }),
            kf("position", 3, { x: "0", y: "0", z: "0" }),
          ],
        },
        [groupUuids.head]: {
          name: "head",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "0", y: "0", z: "0" }),
            kf("rotation", 1.5, { x: "3", y: "0", z: "0" }),
            kf("rotation", 3, { x: "0", y: "0", z: "0" }),
          ],
        },
        [groupUuids.cape]: {
          name: "cape",
          type: "bone",
          rotation_global: false,
          quaternion_interpolation: false,
          keyframes: [
            kf("rotation", 0, { x: "0", y: "0", z: "0" }),
            kf("rotation", 1.5, { x: "4", y: "0", z: "0" }),
            kf("rotation", 3, { x: "0", y: "0", z: "0" }),
          ],
        },
      },
    },
  ],
};

const outFile = join(OUT_DIR, "darth_vader.bbmodel");
writeFileSync(outFile, JSON.stringify(model));
console.log(`Wrote ${outFile} (${elements.length} cubes)`);
