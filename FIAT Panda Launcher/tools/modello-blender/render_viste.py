"""
Render di controllo del modello da più angolazioni (fronte +X).
Uso: blender --background <file.blend> --python render_viste.py -- <cartella_uscita> [prefisso] [eevee]
"""
import sys
import os
import math
import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:]
OUT = argv[0]
PREFIX = argv[1] if len(argv) > 1 else "vista"
USE_EEVEE = len(argv) > 2 and argv[2] == "eevee"
os.makedirs(OUT, exist_ok=True)

scene = bpy.context.scene
scene.render.resolution_x = 900
scene.render.resolution_y = 560
if USE_EEVEE:
    scene.render.engine = 'BLENDER_EEVEE_NEXT'
    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (0.35, 0.37, 0.40, 1)
    bg.inputs[1].default_value = 1.0
    if "Sole" not in bpy.data.objects:
        sun = bpy.data.lights.new("Sole", 'SUN')
        sun.energy = 3.5
        so = bpy.data.objects.new("Sole", sun)
        scene.collection.objects.link(so)
        so.rotation_euler = (math.radians(50), 0, math.radians(30))
else:
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.display.shading.light = 'STUDIO'
    scene.display.shading.color_type = 'MATERIAL'

cam_data = bpy.data.cameras.new("Cam")
cam_data.lens = 50
cam = bpy.data.objects.new("Cam", cam_data)
scene.collection.objects.link(cam)
scene.camera = cam

target = Vector((0, 0, 0.75))
views = {
    "34fronte": (6.5, -5.0, 2.6),
    "lato": (0.0, -8.5, 1.2),
    "fronte": (8.0, 0.0, 1.4),
    "retro": (-8.0, 0.0, 1.4),
    "34retro": (-6.5, 5.0, 2.6),
    "alto": (0.01, -0.01, 9.0),
}
for name, pos in views.items():
    cam.location = Vector(pos)
    direction = target - cam.location
    cam.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = os.path.join(OUT, "%s_%s.png" % (PREFIX, name))
    bpy.ops.render.render(write_still=True)
print("render fatti in", OUT)
