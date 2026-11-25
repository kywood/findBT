#!/usr/bin/env python3
"""Bakes the designer's device glTF models into flat vertex files for the 3D scenes (#133, #86).

Usage: python3 scripts/bake-device-meshes.py <folder with nearo_*.gltf> [output folder]

Each model becomes core/ui/src/main/assets/models/<name>.mesh: little-endian, an int32 vertex
count, then per vertex nine float32 (position xyz, normal xyz, colour rgb in sRGB 0..1), as plain
triangles. Node transforms are applied, material base colours become vertex colours, and the model
is centred on x/z, stood on y = 0 and scaled so its longest side is 1. No textures, no runtime
glTF parser. The source models live in the design share (design/v3/assets3d/devices).
"""
import base64
import json
import math
import struct
import sys
from pathlib import Path

COMPONENT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
# Models the app draws: the find scene (#133) and the map (#86). The UI glyph meshes are not used.
SKIP_PREFIX = "nearo_ui_"


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def node_matrix(node):
    if "matrix" in node:
        m = node["matrix"]  # column-major
        return [[m[c * 4 + r] for c in range(4)] for r in range(4)]
    tx, ty, tz = node.get("translation", [0, 0, 0])
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    sx, sy, sz = node.get("scale", [1, 1, 1])
    rot = [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]
    return [
        [rot[0][0] * sx, rot[0][1] * sy, rot[0][2] * sz, tx],
        [rot[1][0] * sx, rot[1][1] * sy, rot[1][2] * sz, ty],
        [rot[2][0] * sx, rot[2][1] * sy, rot[2][2] * sz, tz],
        [0, 0, 0, 1],
    ]


def inverse_transpose3(m):
    a = [row[:3] for row in m[:3]]
    det = (
        a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1])
        - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
        + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
    )
    cof = [
        [a[1][1] * a[2][2] - a[1][2] * a[2][1], a[1][2] * a[2][0] - a[1][0] * a[2][2], a[1][0] * a[2][1] - a[1][1] * a[2][0]],
        [a[0][2] * a[2][1] - a[0][1] * a[2][2], a[0][0] * a[2][2] - a[0][2] * a[2][0], a[0][1] * a[2][0] - a[0][0] * a[2][1]],
        [a[0][1] * a[1][2] - a[0][2] * a[1][1], a[0][2] * a[1][0] - a[0][0] * a[1][2], a[0][0] * a[1][1] - a[0][1] * a[1][0]],
    ]
    return [[c / det for c in row] for row in cof]


def srgb(linear):
    linear = min(max(linear, 0.0), 1.0)
    return 12.92 * linear if linear <= 0.0031308 else 1.055 * linear ** (1 / 2.4) - 0.055


class Gltf:
    def __init__(self, path):
        self.doc = json.loads(Path(path).read_text())
        self.buffers = []
        for buffer in self.doc["buffers"]:
            uri = buffer["uri"]
            if uri.startswith("data:"):
                self.buffers.append(base64.b64decode(uri.split(",", 1)[1]))
            else:
                self.buffers.append((Path(path).parent / uri).read_bytes())

    def accessor(self, index):
        acc = self.doc["accessors"][index]
        view = self.doc["bufferViews"][acc["bufferView"]]
        fmt = COMPONENT[acc["componentType"]]
        width = WIDTH[acc["type"]]
        size = struct.calcsize(fmt) * width
        stride = view.get("byteStride", size)
        start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        data = self.buffers[view["buffer"]]
        out = []
        for i in range(acc["count"]):
            values = struct.unpack_from("<" + fmt * width, data, start + i * stride)
            out.append(values[0] if width == 1 else values)
        return out

    def triangles(self):
        """Yields (position, normal, colour) per vertex, three per triangle, in scene space."""
        doc = self.doc
        scene = doc["scenes"][doc.get("scene", 0)]
        identity = [[1 if i == j else 0 for j in range(4)] for i in range(4)]
        stack = [(n, identity) for n in scene["nodes"]]
        while stack:
            index, parent = stack.pop()
            node = doc["nodes"][index]
            world = mat_mul(parent, node_matrix(node))
            stack.extend((child, world) for child in node.get("children", []))
            if "mesh" not in node:
                continue
            normal_matrix = inverse_transpose3(world)
            for primitive in doc["meshes"][node["mesh"]]["primitives"]:
                if primitive.get("mode", 4) != 4:
                    raise ValueError("only triangle lists are supported")
                positions = self.accessor(primitive["attributes"]["POSITION"])
                normals = self.accessor(primitive["attributes"]["NORMAL"])
                order = self.accessor(primitive["indices"]) if "indices" in primitive else range(len(positions))
                material = doc.get("materials", [{}])[primitive.get("material", 0)]
                base = material.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1, 1, 1, 1])
                colour = tuple(srgb(c) for c in base[:3])
                for i in order:
                    p = positions[i]
                    n = normals[i]
                    wp = tuple(sum(world[r][c] * p[c] for c in range(3)) + world[r][3] for r in range(3))
                    wn = tuple(sum(normal_matrix[r][c] * n[c] for c in range(3)) for r in range(3))
                    length = math.sqrt(sum(v * v for v in wn)) or 1.0
                    yield wp, tuple(v / length for v in wn), colour


def bake(source, target):
    vertices = list(Gltf(source).triangles())
    low = [min(v[0][axis] for v in vertices) for axis in range(3)]
    high = [max(v[0][axis] for v in vertices) for axis in range(3)]
    scale = 1.0 / max(high[axis] - low[axis] for axis in range(3))
    centre_x = (low[0] + high[0]) / 2
    centre_z = (low[2] + high[2]) / 2
    out = bytearray(struct.pack("<i", len(vertices)))
    for position, normal, colour in vertices:
        out += struct.pack(
            "<9f",
            (position[0] - centre_x) * scale,
            (position[1] - low[1]) * scale,
            (position[2] - centre_z) * scale,
            *normal,
            *colour,
        )
    target.write_bytes(out)
    return len(vertices) // 3, len(out)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    source = Path(sys.argv[1])
    target = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("core/ui/src/main/assets/models")
    target.mkdir(parents=True, exist_ok=True)
    total = 0
    for path in sorted(source.glob("nearo_*.gltf")):
        if path.stem.startswith(SKIP_PREFIX):
            continue
        triangles, size = bake(path, target / (path.stem + ".mesh"))
        total += size
        print(f"{path.stem}: {triangles} triangles, {size} bytes")
    print(f"total {total} bytes")


if __name__ == "__main__":
    main()
