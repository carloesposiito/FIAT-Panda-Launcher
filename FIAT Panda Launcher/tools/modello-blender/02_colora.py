"""
Passo 2: colora il modello per zone e lo esporta in .glb per il launcher.

Il modello STL è un pezzo unico: ogni faccia riceve un materiale in base a dove si trova
(centro della faccia, in metri, muso verso +X, Z in alto) e a dove guarda (normale).
Le zone sono state misurate sui render del modello (vedi render_viste.py).

Uso: blender --background panda_base.blend --python 02_colora.py -- <cartella_uscita> [colore]
     colore: nero (predefinito), rosso, bianco, grigio, blu, giallo, verde
"""
import sys
import os
import bpy
import bmesh

argv = sys.argv[sys.argv.index("--") + 1:]
OUT = argv[0]
COLORE = argv[1] if len(argv) > 1 else "nero"
os.makedirs(OUT, exist_ok=True)

# ----------------------------------------------------------------- materiali (PBR)
VERNICI = {
    "nero":   ((0.010, 0.010, 0.012), 0.55, 0.30),
    "rosso":  ((0.45, 0.02, 0.02), 0.10, 0.30),
    "bianco": ((0.80, 0.80, 0.78), 0.00, 0.30),
    "grigio": ((0.25, 0.26, 0.27), 0.60, 0.30),
    "blu":    ((0.02, 0.06, 0.25), 0.50, 0.30),
    "giallo": ((0.80, 0.55, 0.02), 0.10, 0.30),
    "verde":  ((0.03, 0.18, 0.08), 0.40, 0.30),
}
paint_rgb, paint_metal, paint_rough = VERNICI.get(COLORE, VERNICI["nero"])


def material(name, rgb, metallic=0.0, roughness=0.5, coat=0.0, emission=None, strength=0.0):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*rgb, 1)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    if coat > 0:
        bsdf.inputs["Coat Weight"].default_value = coat
        bsdf.inputs["Coat Roughness"].default_value = 0.04
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    m.diffuse_color = (*rgb, 1)   # colore anche nel render Workbench
    return m


MATS = [
    material("Carrozzeria", paint_rgb, paint_metal, paint_rough, coat=1.0),      # 0
    material("Vetro", (0.015, 0.020, 0.026), 0.0, 0.04, coat=1.0),               # 1
    material("Plastica", (0.035, 0.036, 0.040), 0.0, 0.65),                      # 2
    material("Gomma", (0.018, 0.018, 0.018), 0.0, 0.90),                         # 3
    material("Cerchio", (0.55, 0.56, 0.58), 0.85, 0.35),                         # 4
    material("Faro", (0.80, 0.82, 0.85), 0.2, 0.08, emission=(1, 0.97, 0.9), strength=0.15),   # 5
    material("Fanale", (0.55, 0.02, 0.02), 0.0, 0.12, emission=(0.9, 0.05, 0.03), strength=0.25),  # 6
    material("Arancio", (0.85, 0.40, 0.02), 0.0, 0.15, emission=(1, 0.45, 0.02), strength=0.15),   # 7
    material("Cromo", (0.80, 0.80, 0.82), 1.0, 0.15),                            # 8
]
BODY, GLASS, PLASTIC, TIRE, RIM, HEAD, TAIL, INDICATOR, CHROME = range(9)

# ----------------------------------------------------------------- zone (metri)
WHEELS_X = (1.10, -1.30)     # centro ruote anteriore e posteriore
WHEEL_Z = 0.275              # centro ruota = raggio
TIRE_R = 0.300
RIM_R = 0.200
BELT_Z = 0.975               # linea di cintura (sotto i finestrini)
ROOF_Z = 1.47                # bordo superiore dei finestrini


def a_pillar_x(z):
    """Montante anteriore: da (0.50, 0.98) a (0.05, 1.47)."""
    return 0.50 - (z - 0.98) * (0.45 / 0.49)


def classify(c, n):
    x, y, z = c
    ay = abs(y)
    # antenna e tutto ciò che sporge sopra il tetto
    if z > 1.57:
        return PLASTIC
    # ruote: gomma e copricerchio sui fianchi esterni
    for wx in WHEELS_X:
        d = ((x - wx) ** 2 + (z - WHEEL_Z) ** 2) ** 0.5
        if d < TIRE_R + 0.01 and ay > 0.50:
            if d < RIM_R and abs(n.y) > 0.45 and ay > 0.60:
                return RIM
            return TIRE
    # sottoscocca
    if z < 0.17:
        return PLASTIC
    # finestrini laterali e montanti (sulla Panda la cornice dei vetri è nera)
    if BELT_Z < z < ROOF_Z and -1.22 < x < a_pillar_x(z) + 0.02 and ay > 0.50 and abs(n.y) > 0.55:
        return GLASS
    # parabrezza: sul piano del montante anteriore, rivolto in avanti e in alto
    if BELT_Z - 0.01 < z < ROOF_Z + 0.03 and ay < 0.66 and n.x > 0.35 and n.z > 0.25 \
            and a_pillar_x(z) - 0.20 < x < a_pillar_x(z) + 0.45 and x > 0.0:
        return GLASS
    # lunotto
    if x < -1.55 and 1.00 < z < 1.45 and ay < 0.55 and n.x < -0.5:
        return GLASS
    # fanali posteriori verticali ai lati del portellone (girano un po' sul fianco)
    if x < -1.56 and 0.60 < z < 1.20 and ay > 0.48 and n.x < 0.15:
        return TAIL
    # fari anteriori agli angoli (girano sul fianco), con le frecce arancioni all'esterno
    if x > 1.40 and 0.62 < z < 0.90 and 0.30 < ay < 0.80 and n.x > -0.05 and n.z < 0.75:
        return INDICATOR if (ay > 0.70 and x < 1.62) else HEAD
    # calandra al centro del frontale (stemma cromato)
    if x > 1.62 and 0.58 < z < 0.80 and ay < 0.36 and n.x > 0.4:
        return CHROME if (ay < 0.06 and 0.66 < z < 0.74) else PLASTIC
    # fascia bassa dei paraurti e protezioni laterali
    if (x > 1.55 or x < -1.55) and z < 0.40:
        return PLASTIC
    if ay > 0.70 and 0.30 < z < 0.44 and -1.0 < x < 0.85:
        return PLASTIC
    return BODY


obj = bpy.data.objects["Panda"]
me = obj.data
me.materials.clear()
for m in MATS:
    me.materials.append(m)

bm = bmesh.new()
bm.from_mesh(me)
counts = [0] * len(MATS)
for f in bm.faces:
    idx = classify(f.calc_center_median(), f.normal)
    f.material_index = idx
    counts[idx] += 1
bm.to_mesh(me)
bm.free()
me.update()
for m, c in zip(MATS, counts):
    print("%-12s %6d facce" % (m.name, c))

bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "panda_colorata.blend"))

# ----------------------------------------------------------------- esportazione glTF binario
bpy.ops.object.select_all(action='DESELECT')
obj.select_set(True)
bpy.context.view_layer.objects.active = obj
bpy.ops.export_scene.gltf(
    filepath=os.path.join(OUT, "panda.glb"),
    export_format='GLB',
    use_selection=True,
    export_yup=True,
    export_apply=True,
    export_normals=True,
    export_materials='EXPORT',
)
print("esportato", os.path.join(OUT, "panda.glb"))
