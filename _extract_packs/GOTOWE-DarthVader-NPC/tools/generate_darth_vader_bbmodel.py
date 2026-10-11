#!/usr/bin/env python3
"""Generuje darth_vader.bbmodel (Blockbench 5.0 free) pod ModelEngine 1.21.x."""
import json
import uuid
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "ModelEngine" / "blueprints" / "darth_vader"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def uid():
    return str(uuid.uuid4())


def box_faces(uo, vo, w, h, d):
    x, y = uo, vo
    return {
        "north": {"uv": [x + d, y + d, x + d + w, y + d + h], "texture": 0},
        "east": {"uv": [x, y + d, x + d, y + d + h], "texture": 0},
        "south": {"uv": [x + d + w, y + d, x + 2 * d + w, y + d + h], "texture": 0},
        "west": {"uv": [x + d + w, y + d, x + 2 * d + w, y + d + h], "texture": 0},
        "up": {"uv": [x + d, y, x + d + w, y + d], "texture": 0},
        "down": {"uv": [x + d + w, y, x + 2 * d + w, y + d], "texture": 0},
    }


def cube(name, fr, to, origin, uv_off=(0, 0), rotation=None, inflate=0, visibility=True, color=4):
    w = int(round(to[0] - fr[0]))
    h = int(round(to[1] - fr[1]))
    d = int(round(to[2] - fr[2]))
    w, h, d = max(w, 1), max(h, 1), max(d, 1)
    el = {
        "name": name,
        "box_uv": True,
        "render_order": "default",
        "locked": False,
        "allow_mirror_modeling": True,
        "from": list(fr),
        "to": list(to),
        "autouv": 0,
        "color": color,
        "origin": list(origin),
        "uv_offset": list(uv_off),
        "faces": box_faces(uv_off[0], uv_off[1], w, h, d),
        "type": "cube",
        "uuid": uid(),
    }
    if rotation:
        el["rotation"] = list(rotation)
    if inflate:
        el["inflate"] = inflate
    if not visibility:
        el["visibility"] = False
    return el


elements = []
by_group = {
    "head": [],
    "body": [],
    "left_arm": [],
    "right_arm": [],
    "left_leg": [],
    "right_leg": [],
    "cape": [],
    "collar": [],
    "hitbox": [],
    "lights": [],
}

# --- Hitbox (ModelEngine) ---
hb = cube(
    "hitbox",
    (-5, 0, -5),
    (5, 36, 5),
    (0, 18, 0),
    (0, 56),
    visibility=False,
    color=3,
)
by_group["hitbox"].append(hb)

# --- Legs (boots) ---
for sx, gx in [(-3.5, "left_leg"), (3.5, "right_leg")]:
    by_group[gx].append(cube("shin", (sx - 1.5, 0, -1.5), (sx + 1.5, 10, 1.5), (sx, 5, 0), (16, 80)))
    by_group[gx].append(cube("boot_top", (sx - 2, 10, -2), (sx + 2, 14, 2), (sx, 12, 0), (32, 80)))
    by_group[gx].append(cube("boot_toe", (sx - 2, 0, -3), (sx + 2, 4, -1.5), (sx, 2, -2), (48, 80)))

# --- Torso ---
by_group["body"].append(cube("torso_main", (-5, 14, -3), (5, 24, 3), (0, 19, 0), (0, 0)))
by_group["body"].append(cube("torso_rib_l", (-5.5, 15, -2.5), (-4.5, 23, 2.5), (-5, 19, 0), (64, 0)))
by_group["body"].append(cube("torso_rib_r", (4.5, 15, -2.5), (5.5, 23, 2.5), (5, 19, 0), (64, 0)))
by_group["body"].append(cube("chest_panel", (-2.5, 17, -3.2), (2.5, 21, -2.8), (0, 19, -3), (80, 16)))
by_group["body"].append(cube("belt", (-5.5, 13, -3), (5.5, 15, 3), (0, 14, 0), (96, 0)))
by_group["body"].append(cube("belt_box_l", (-5.5, 13.2, -1), (-3.5, 14.8, 1), (-4.5, 14, 0), (96, 16)))
by_group["body"].append(cube("belt_box_r", (3.5, 13.2, -1), (5.5, 14.8, 1), (4.5, 14, 0), (96, 16)))
by_group["body"].append(cube("codpiece", (-1.5, 12, -1.5), (1.5, 14, 1.5), (0, 13, 0), (112, 0)))

# --- Shoulder pauldrons (on arms group roots) ---
by_group["left_arm"].append(cube("shoulder_l", (-8, 22, -3), (-4, 26, 3), (-6, 24, 0), (0, 32)))
by_group["right_arm"].append(cube("shoulder_r", (4, 22, -3), (8, 26, 3), (6, 24, 0), (0, 32)))
by_group["left_arm"].append(cube("arm_l", (-8, 14, -2), (-4, 22, 2), (-6, 18, 0), (16, 32)))
by_group["right_arm"].append(cube("arm_r", (4, 14, -2), (8, 22, 2), (6, 18, 0), (16, 32)))
by_group["left_arm"].append(cube("glove_l", (-8, 12, -2), (-4, 14, 2), (-6, 13, 0), (32, 32)))
by_group["right_arm"].append(cube("glove_r", (4, 12, -2), (8, 14, 2), (6, 13, 0), (32, 32)))

# --- Collar / cape mount ---
by_group["collar"].append(cube("collar", (-6, 23, -3), (6, 25, 3), (0, 24, 0), (48, 48)))

# --- Cape segments ---
for i, y in enumerate(range(24, 36, 3)):
    w = 9 + i * 0.3
    by_group["cape"].append(
        cube(
            f"cape_{i}",
            (-w, y, 2.5),
            (w, y + 2.8, 2.55),
            (0, y + 1, 2.5),
            (64, 64),
        )
    )

# --- Helmet (voxel stack) ---
head_y = 24
by_group["head"].append(cube("neck", (-2.5, head_y, -2), (2.5, head_y + 2, 2), (0, head_y + 1, 0), (80, 48)))
by_group["head"].append(cube("helmet_core", (-5, head_y + 2, -4), (5, head_y + 12, 4), (0, head_y + 7, 0), (0, 48)))
by_group["head"].append(cube("helmet_ridge", (-0.5, head_y + 10, -4.5), (0.5, head_y + 14, 4.5), (0, head_y + 12, 0), (16, 48)))
by_group["head"].append(cube("face_mask", (-4.5, head_y + 2, -4.5), (4.5, head_y + 9, -3.5), (0, head_y + 5, -4), (32, 48)))
by_group["head"].append(cube("mouth_grille", (-2, head_y + 3, -4.6), (2, head_y + 6, -4.2), (0, head_y + 4.5, -4.4), (48, 48)))
# Cheek flares
by_group["head"].append(
    cube("cheek_l", (-6, head_y + 4, -3), (-4.5, head_y + 9, 2), (-5.2, head_y + 6, 0), (64, 48), rotation=[0, 12, 0])
)
by_group["head"].append(
    cube("cheek_r", (4.5, head_y + 4, -3), (6, head_y + 9, 2), (5.2, head_y + 6, 0), (64, 48), rotation=[0, -12, 0])
)
# Helmet back flare
by_group["head"].append(
    cube("helmet_back", (-4, head_y + 6, 3), (4, head_y + 13, 7), (0, head_y + 9, 5), (96, 48), rotation=[-18, 0, 0])
)
# Eyes (recessed)
by_group["head"].append(cube("eye_l", (-3.5, head_y + 7, -4.55), (-1.5, head_y + 8.5, -4.35), (-2.5, head_y + 7.8, -4.5), (112, 48)))
by_group["head"].append(cube("eye_r", (1.5, head_y + 7, -4.55), (3.5, head_y + 8.5, -4.35), (2.5, head_y + 7.8, -4.5), (112, 48)))

# Emissive-style lights (small cubes)
by_group["lights"].append(cube("light_chest_r", (1.2, 18.5, -3.25), (1.8, 19.1, -3.05), (1.5, 18.8, -3.15), (120, 8), color=6))
by_group["lights"].append(cube("light_chest_g", (0.2, 18.5, -3.25), (0.8, 19.1, -3.05), (0.5, 18.8, -3.15), (120, 12), color=5))
by_group["lights"].append(cube("light_belt_l", (-4.8, 13.8, 0.2), (-4.2, 14.2, 0.6), (-4.5, 14, 0.4), (120, 16), color=6))

# Flatten to elements list
group_uuids = {}
for gname in [
    "body",
    "head",
    "left_arm",
    "right_arm",
    "left_leg",
    "right_leg",
    "cape",
    "collar",
    "hitbox",
    "lights",
]:
    group_uuids[gname] = uid()

element_uuids_by_group = {k: [e["uuid"] for e in v] for k, v in by_group.items()}
for v in by_group.values():
    elements.extend(v)

groups = []
origins = {
    "body": [0, 18, 0],
    "head": [0, 28, 0],
    "left_arm": [-6, 22, 0],
    "right_arm": [6, 22, 0],
    "left_leg": [-3.5, 12, 0],
    "right_leg": [3.5, 12, 0],
    "cape": [0, 28, 2.5],
    "collar": [0, 24, 0],
    "hitbox": [0, 18, 0],
    "lights": [0, 18, -3],
}
for gname, gu in group_uuids.items():
    groups.append(
        {
            "uuid": gu,
            "export": True,
            "locked": False,
            "origin": origins[gname],
            "rotation": [0, 0, 0],
            "color": 0,
            "name": gname,
            "children": [],
            "reset": False,
            "shade": True,
            "mirror_uv": False,
            "selected": False,
            "visibility": gname != "hitbox",
            "autouv": 0,
            "isOpen": gname == "body",
        }
    )

outliner = [
    {
        "uuid": group_uuids["body"],
        "isOpen": True,
        "children": [
            {
                "uuid": group_uuids["head"],
                "isOpen": False,
                "children": element_uuids_by_group["head"],
            },
            {
                "uuid": group_uuids["collar"],
                "isOpen": False,
                "children": element_uuids_by_group["collar"],
            },
            {
                "uuid": group_uuids["left_arm"],
                "isOpen": False,
                "children": element_uuids_by_group["left_arm"],
            },
            {
                "uuid": group_uuids["right_arm"],
                "isOpen": False,
                "children": element_uuids_by_group["right_arm"],
            },
            {
                "uuid": group_uuids["left_leg"],
                "isOpen": False,
                "children": element_uuids_by_group["left_leg"],
            },
            {
                "uuid": group_uuids["right_leg"],
                "isOpen": False,
                "children": element_uuids_by_group["right_leg"],
            },
            {
                "uuid": group_uuids["cape"],
                "isOpen": False,
                "children": element_uuids_by_group["cape"],
            },
            {
                "uuid": group_uuids["lights"],
                "isOpen": False,
                "children": element_uuids_by_group["lights"],
            },
            *element_uuids_by_group["body"],
        ],
    },
    {
        "uuid": group_uuids["hitbox"],
        "isOpen": True,
        "children": element_uuids_by_group["hitbox"],
    },
]

tex_uuid = uid()
anim_uuid = uid()

def kf(channel, time, value, interp="catmullrom"):
    return {
        "channel": channel,
        "data_points": [value],
        "uuid": uid(),
        "time": time,
        "color": -1,
        "interpolation": interp,
    }


animations = [
    {
        "uuid": anim_uuid,
        "name": "idle",
        "loop": "loop",
        "override": False,
        "length": 3,
        "snapping": 20,
        "selected": True,
        "group_name": "",
        "anim_time_update": "",
        "blend_weight": "",
        "start_delay": "",
        "loop_delay": "",
        "animators": {
            group_uuids["body"]: {
                "name": "body",
                "type": "bone",
                "rotation_global": False,
                "quaternion_interpolation": False,
                "keyframes": [
                    kf("position", 0, {"x": "0", "y": "0", "z": "0"}),
                    kf("position", 1.5, {"x": "0", "y": "-0.35", "z": "0"}),
                    kf("position", 3, {"x": "0", "y": "0", "z": "0"}),
                ],
            },
            group_uuids["head"]: {
                "name": "head",
                "type": "bone",
                "rotation_global": False,
                "quaternion_interpolation": False,
                "keyframes": [
                    kf("rotation", 0, {"x": "0", "y": "0", "z": "0"}),
                    kf("rotation", 1.5, {"x": "3", "y": "0", "z": "0"}),
                    kf("rotation", 3, {"x": "0", "y": "0", "z": "0"}),
                ],
            },
            group_uuids["cape"]: {
                "name": "cape",
                "type": "bone",
                "rotation_global": False,
                "quaternion_interpolation": False,
                "keyframes": [
                    kf("rotation", 0, {"x": "0", "y": "0", "z": "0"}),
                    kf("rotation", 1.5, {"x": "4", "y": "0", "z": "0"}),
                    kf("rotation", 3, {"x": "0", "y": "0", "z": "0"}),
                ],
            },
            group_uuids["right_arm"]: {
                "name": "right_arm",
                "type": "bone",
                "rotation_global": False,
                "quaternion_interpolation": False,
                "keyframes": [
                    kf("rotation", 0, {"x": "0", "y": "0", "z": "0"}),
                    kf("rotation", 3, {"x": "0", "y": "0", "z": "0"}),
                ],
            },
            group_uuids["left_arm"]: {
                "name": "left_arm",
                "type": "bone",
                "rotation_global": False,
                "quaternion_interpolation": False,
                "keyframes": [
                    kf("rotation", 0, {"x": "0", "y": "0", "z": "0"}),
                    kf("rotation", 3, {"x": "0", "y": "0", "z": "0"}),
                ],
            },
        },
    }
]

model = {
    "meta": {
        "format_version": "5.0",
        "model_format": "free",
        "box_uv": True,
    },
    "name": "darth_vader",
    "visible_box": [1, 1, 0],
    "variable_placeholders": "",
    "variable_placeholder_buttons": [],
    "timeline_setups": [],
    "unhandled_root_fields": {},
    "resolution": {"width": 128, "height": 128},
    "elements": elements,
    "groups": groups,
    "outliner": outliner,
    "textures": [
        {
            "name": "darth_vader.png",
            "relative_path": "darth_vader.png",
            "folder": "block",
            "namespace": "",
            "id": "0",
            "group": "",
            "width": 128,
            "height": 128,
            "uv_width": 128,
            "uv_height": 128,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            "pbr_channel": "color",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": False,
            "saved": True,
            "uuid": tex_uuid,
            "path": "darth_vader.png",
        }
    ],
    "animations": animations,
}

out_file = OUT_DIR / "darth_vader.bbmodel"
out_file.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
print(f"Wrote {out_file} ({len(elements)} cubes)")
