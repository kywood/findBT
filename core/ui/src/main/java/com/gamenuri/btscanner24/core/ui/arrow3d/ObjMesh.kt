package com.gamenuri.btscanner24.core.ui.arrow3d

import java.io.InputStream

/**
 * Triangles of a Wavefront OBJ file as interleaved position + normal floats (6 per vertex), ready
 * for glDrawArrays. For modelled meshes such as the device map's (#86); the arrow is [ArrowMesh].
 * Reads only what the Nearo meshes use: `v`, `vn` and triangular `f a//n` faces.
 */
internal object ObjMesh {
    const val FLOATS_PER_VERTEX = 6

    fun parse(input: InputStream): FloatArray {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val out = ArrayList<Float>()
        input.bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.trim().split(' ').filter { it.isNotEmpty() }
                when (parts.firstOrNull()) {
                    "v" -> for (i in 1..3) positions += parts[i].toFloat()
                    "vn" -> for (i in 1..3) normals += parts[i].toFloat()
                    "f" -> for (i in 1..3) {
                        val refs = parts[i].split('/')
                        val p = (refs[0].toInt() - 1) * 3
                        val n = (refs[2].toInt() - 1) * 3
                        for (k in 0..2) out += positions[p + k]
                        for (k in 0..2) out += normals[n + k]
                    }
                }
            }
        }
        return out.toFloatArray()
    }
}
