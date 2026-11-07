"""
Passo 1: importa l'STL della Panda 169 (Thingiverse 7087348, lowendguru, CC BY-SA),
lo porta in metri con il muso verso +X e le ruote a terra (Z = 0), lo alleggerisce
e salva un .blend di lavoro. Stampa le misure utili per la colorazione.

Uso: blender --background --python 01_prepara.py -- <file.stl> <cartella_uscita>
"""
import sys
import os
import bpy
import bmesh
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:]
STL, OUT = argv[0], argv[1]
os.makedirs(OUT, exist_ok=True)

REAL_LENGTH_M = 3.538      # lunghezza reale della Panda 169
TARGET_TRIS = 110_000      # leggero per l'autoradio

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.wm.stl_import(filepath=STL)
obj = bpy.context.selected_objects[0]
obj.name = "Panda"
me = obj.data

# --- orientamento: l'asse più lungo è la lunghezza dell'auto ---
xs = [v.co.x for v in me.vertices]
ys = [v.co.y for v in me.vertices]
zs = [v.co.z for v in me.vertices]
size = Vector((max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)))
print("dimensioni originali", tuple(round(s, 1) for s in size))
long_axis = 0 if size.x >= size.y else 1

# davanti = estremo con il "cofano" più basso (altezza media delle parti alte vicino all'estremo)
coords = [v.co.copy() for v in me.vertices]
lo = min(c[long_axis] for c in coords)
hi = max(c[long_axis] for c in coords)
L = hi - lo
def top_height(a, b):
    sel = [c.z for c in coords if a <= c[long_axis] <= b]
    sel.sort()
    return sel[int(len(sel) * 0.95)] if sel else 0
h_lo = top_height(lo + 0.05 * L, lo + 0.20 * L)
h_hi = top_height(hi - 0.20 * L, hi - 0.05 * L)
front_is_hi = h_hi < h_lo
print("altezza vicino agli estremi:", round(h_lo, 1), round(h_hi, 1), "muso verso", "max" if front_is_hi else "min")

# rotazione: asse lungo -> +X col muso in avanti
if long_axis == 0:
    rot = Matrix.Identity(4) if front_is_hi else Matrix.Rotation(3.14159265, 4, 'Z')
else:
    rot = Matrix.Rotation(-1.5707963, 4, 'Z') if front_is_hi else Matrix.Rotation(1.5707963, 4, 'Z')
me.transform(rot)

# scala in metri e centratura (centro in X/Y, ruote a Z = 0)
xs = [v.co.x for v in me.vertices]
scale = REAL_LENGTH_M / (max(xs) - min(xs))
me.transform(Matrix.Scale(scale, 4))
xs = [v.co.x for v in me.vertices]
ys = [v.co.y for v in me.vertices]
zs = [v.co.z for v in me.vertices]
me.transform(Matrix.Translation((-(max(xs) + min(xs)) / 2, -(max(ys) + min(ys)) / 2, -min(zs))))
me.update()

# --- alleggerimento ---
tris = sum(len(p.vertices) - 2 for p in me.polygons)
ratio = min(1.0, TARGET_TRIS / tris)
mod = obj.modifiers.new("Decimate", 'DECIMATE')
mod.ratio = ratio
bpy.context.view_layer.objects.active = obj
bpy.ops.object.modifier_apply(modifier=mod.name)
bm = bmesh.new()
bm.from_mesh(me)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.0005)
bm.to_mesh(me)
bm.free()
for p in me.polygons:
    p.use_smooth = True
me.update()
tris2 = sum(len(p.vertices) - 2 for p in me.polygons)
print("triangoli:", tris, "->", tris2)

xs = [v.co.x for v in me.vertices]
ys = [v.co.y for v in me.vertices]
zs = [v.co.z for v in me.vertices]
print("misure (m): lunghezza %.3f larghezza %.3f altezza %.3f" % (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)))

# --- profilo utile per trovare ruote e linea di cintura ---
# altezza minima della carrozzeria (escluse le ruote) lungo X: dove scende a terra ci sono le ruote
import collections
bins = collections.defaultdict(list)
for v in me.vertices:
    if abs(v.co.y) > 0.55:   # fianchi esterni (dove stanno le gomme)
        bins[round(v.co.x, 1)].append(v.co.z)
print("x  -> z minima sui fianchi esterni")
for x in sorted(bins):
    print("  %.1f  %.3f" % (x, min(bins[x])))

bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "panda_base.blend"))
print("salvato", os.path.join(OUT, "panda_base.blend"))
