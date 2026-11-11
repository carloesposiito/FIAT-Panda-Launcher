// Genera il modello 3D provvisorio della Panda 169 (panda.glb) con le stesse forme del prototipo web.
// Uso:  npm install  &&  npm run genera
// Quando avrai un modello vero (es. da Sketchfab) basta sostituire il file .glb e questo script non serve più.
//
// Convenzioni del modello generato: metri, asse Y verso l'alto, muso dell'auto verso +X,
// ruote appoggiate a Y = 0.

import * as THREE from 'three';
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// GLTFExporter usa FileReader, che in Node non esiste: ne basta una versione minima.
globalThis.FileReader = class {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((buf) => { this.result = buf; this.onloadend && this.onloadend(); });
  }
  readAsDataURL(blob) {
    blob.arrayBuffer().then((buf) => {
      this.result = 'data:application/octet-stream;base64,' + Buffer.from(buf).toString('base64');
      this.onloadend && this.onloadend();
    });
  }
};

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, '../../app/src/main/assets/models/panda.glb');

const clamp01 = (v) => Math.min(1, Math.max(0, v));

/** Estrude un profilo laterale (piano XY) lungo Z, centrato sull'asse. */
function extrude(shape, depth, bevelSize, bevelThickness) {
  const g = new THREE.ExtrudeGeometry(shape, {
    depth, bevelEnabled: true, bevelSize, bevelThickness,
    bevelSegments: 5, curveSegments: 24, steps: 1,
  });
  g.translate(0, 0, -depth / 2);
  return g;
}

/** Restringe la larghezza (Z) in funzione di X e Y: serve per arrotondare muso e tetto. */
function warp(g, fn) {
  const p = g.attributes.position;
  for (let i = 0; i < p.count; i++) p.setZ(i, p.getZ(i) * fn(p.getX(i), p.getY(i)));
}

/** Unisce i vertici coincidenti e ricalcola le normali, così le curve risultano lisce. */
function smooth(g) {
  const pos = g.attributes.position, map = new Map(), verts = [], idx = [];
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
    const key = x.toFixed(4) + ',' + y.toFixed(4) + ',' + z.toFixed(4);
    let j = map.get(key);
    if (j === undefined) { j = verts.length / 3; map.set(key, j); verts.push(x, y, z); }
    idx.push(j);
  }
  const n = new THREE.BufferGeometry();
  n.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
  n.setIndex(idx);
  n.computeVertexNormals();
  return n;
}

// Misure reali della 169: 3,54 m × 1,58 m × 1,54 m, passo 2,30 m.
function buildPanda() {
  const car = new THREE.Group();
  car.name = 'Panda169';

  const paint = new THREE.MeshPhysicalMaterial({ name: 'Vernice nera', color: 0x060708, metalness: 0.55, roughness: 0.32, clearcoat: 1, clearcoatRoughness: 0.03 });
  const glass = new THREE.MeshPhysicalMaterial({ name: 'Vetri', color: 0x06090c, metalness: 0.2, roughness: 0.03, clearcoat: 1 });
  const trim = new THREE.MeshStandardMaterial({ name: 'Plastica nera', color: 0x0f1012, roughness: 0.7 });
  const plastic = new THREE.MeshStandardMaterial({ name: 'Plastica grigia', color: 0x1c1e21, roughness: 0.85 });
  const rubber = new THREE.MeshStandardMaterial({ name: 'Gomma', color: 0x0d0d0e, roughness: 0.95 });
  const alloy = new THREE.MeshStandardMaterial({ name: 'Cerchi', color: 0xbfc4cb, metalness: 1, roughness: 0.26 });
  const lampF = new THREE.MeshPhysicalMaterial({ name: 'Fari', color: 0xdde4ec, metalness: 0.3, roughness: 0.06, clearcoat: 1, emissive: 0xaec4dc, emissiveIntensity: 0.22 });
  const lampR = new THREE.MeshPhysicalMaterial({ name: 'Fanali', color: 0x6e0c0c, roughness: 0.12, clearcoat: 1, emissive: 0xb31616, emissiveIntensity: 0.4 });

  const add = (geo, mat, x, y, z, rx = 0, ry = 0, rz = 0) => {
    const m = new THREE.Mesh(geo, mat);
    m.position.set(x, y, z);
    m.rotation.set(rx, ry, rz);
    car.add(m);
    return m;
  };
  const box = (sx, sy, sz) => new THREE.BoxGeometry(sx, sy, sz);

  // Scocca inferiore: profilo laterale con i passaruota.
  const s = new THREE.Shape();
  s.moveTo(-1.77, 0.30);
  s.lineTo(-1.77, 1.00);
  s.lineTo(0.95, 1.00);
  s.lineTo(1.50, 0.92);
  s.quadraticCurveTo(1.70, 0.88, 1.74, 0.80);
  s.lineTo(1.79, 0.56);
  s.lineTo(1.80, 0.32);
  s.quadraticCurveTo(1.80, 0.22, 1.70, 0.22);
  s.lineTo(1.50, 0.22);
  s.absarc(1.15, 0.29, 0.36, -0.196, Math.PI + 0.196, false);
  s.lineTo(-0.797, 0.22);
  s.absarc(-1.15, 0.29, 0.36, -0.196, Math.PI + 0.196, false);
  s.lineTo(-1.70, 0.22);
  s.quadraticCurveTo(-1.77, 0.22, -1.77, 0.30);
  const lower = extrude(s, 1.44, 0.035, 0.07);
  warp(lower, (x, y) => {
    let f = 1 - 0.03 * clamp01((y - 0.75) / 0.3) - 0.03 * clamp01((0.4 - y) / 0.2);
    f *= 1 - 0.07 * Math.pow(clamp01((x - 1.5) / 0.33), 2) - 0.03 * Math.pow(clamp01((-x - 1.55) / 0.25), 2);
    return f;
  });
  add(smooth(lower), paint, 0, 0, 0).name = 'Scocca';

  // Abitacolo (vetri), più stretto verso il tetto.
  const c = new THREE.Shape();
  c.moveTo(-1.74, 0.98);
  c.lineTo(0.98, 0.98);
  c.lineTo(0.40, 1.49);
  c.quadraticCurveTo(0.33, 1.53, 0.20, 1.53);
  c.lineTo(-1.55, 1.52);
  c.quadraticCurveTo(-1.68, 1.51, -1.70, 1.42);
  c.lineTo(-1.74, 0.98);
  const TAPER = 0.17, CAB = 0.76;
  const cabHalf = (y) => CAB * (1 - TAPER * clamp01((y - 1.0) / 0.53));
  const cab = extrude(c, 1.42, 0.03, 0.05);
  warp(cab, (x, y) => (1 - TAPER * clamp01((y - 1.0) / 0.53)) * (1 - 0.03 * clamp01((-x - 1.55) / 0.2)));
  add(smooth(cab), glass, 0, 0, 0).name = 'Vetri';

  // Tetto e barre portapacchi.
  add(box(1.96, 0.03, 1.26), paint, -0.6, 1.562, 0);
  for (const sd of [-1, 1]) {
    add(box(1.72, 0.028, 0.034), plastic, -0.62, 1.625, sd * 0.56);
    add(box(0.08, 0.06, 0.04), plastic, -1.42, 1.595, sd * 0.56);
    add(box(0.08, 0.06, 0.04), plastic, 0.18, 1.595, sd * 0.56);
  }

  // Montanti appoggiati sul fianco inclinato dei vetri.
  const lean = Math.atan(CAB * TAPER / 0.53);
  const sidePiece = (mat, x0, x1, y0, y1, sd) => {
    const yc = (y0 + y1) / 2;
    add(box(x1 - x0, y1 - y0, 0.03), mat, (x0 + x1) / 2, yc, sd * (cabHalf(yc) + 0.012), -sd * lean);
  };
  for (const sd of [-1, 1]) {
    sidePiece(paint, -1.73, -1.40, 0.99, 1.47, sd); // montante posteriore in tinta
    sidePiece(trim, -0.36, -0.27, 0.99, 1.49, sd);  // montante centrale nero
  }

  // Fanali posteriori verticali, fari e calandra.
  for (const sd of [-1, 1]) add(box(0.12, 0.68, 0.14), lampR, -1.77, 0.96, sd * 0.655);
  const fa = Math.atan2(-0.24, 0.05), nx = 0.98, ny = 0.20;
  for (const sd of [-1, 1]) add(box(0.20, 0.035, 0.34), lampF, 1.765 + nx * 0.04, 0.68 + ny * 0.04, sd * 0.52, 0, 0, fa);
  add(box(0.09, 0.03, 0.6), trim, 1.765 + nx * 0.035, 0.70 + ny * 0.035, 0, 0, 0, fa);

  // Paraurti, fasce paracolpi, minigonne, maniglie, specchietti.
  add(box(0.12, 0.24, 1.46), plastic, 1.80, 0.34, 0);
  add(box(0.03, 0.08, 0.8), trim, 1.862, 0.34, 0);
  add(box(0.12, 0.24, 1.46), plastic, -1.80, 0.34, 0);
  for (const sd of [-1, 1]) {
    add(box(1.44, 0.08, 0.03), plastic, 0, 0.54, sd * 0.796);
    add(box(1.56, 0.11, 0.04), plastic, 0, 0.245, sd * 0.775);
    add(box(0.006, 0.62, 0.006), trim, -0.31, 0.66, sd * 0.792);
    add(box(0.12, 0.025, 0.02), trim, 0.12, 0.88, sd * 0.775);
    add(box(0.12, 0.025, 0.02), trim, -0.86, 0.88, sd * 0.775);
    add(box(0.13, 0.09, 0.15), trim, 0.80, 1.08, sd * 0.86);
    add(box(0.06, 0.03, 0.08), trim, 0.82, 1.05, sd * 0.77);
  }

  // Ruote e passaruota.
  for (const x of [1.15, -1.15]) {
    add(new THREE.CylinderGeometry(0.33, 0.33, 1.24, 32), trim, x, 0.29, 0, Math.PI / 2);
    for (const sd of [-1, 1]) {
      const w = new THREE.Group();
      w.name = 'Ruota';
      w.position.set(x, 0.29, sd * 0.69);
      const tire = new THREE.Mesh(new THREE.CylinderGeometry(0.29, 0.29, 0.185, 40), rubber);
      tire.rotation.x = Math.PI / 2; w.add(tire);
      const rim = new THREE.Mesh(new THREE.CylinderGeometry(0.19, 0.19, 0.02, 36), alloy);
      rim.rotation.x = Math.PI / 2; rim.position.z = sd * 0.085; w.add(rim);
      for (let k = 0; k < 5; k++) {
        const a = (k / 5) * Math.PI * 2 + 0.3;
        const h = new THREE.Mesh(new THREE.CylinderGeometry(0.048, 0.048, 0.024, 16), rubber);
        h.rotation.x = Math.PI / 2; h.position.set(Math.cos(a) * 0.118, Math.sin(a) * 0.118, sd * 0.085); w.add(h);
      }
      const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 0.03, 20), alloy);
      cap.rotation.x = Math.PI / 2; cap.position.z = sd * 0.09; w.add(cap);
      car.add(w);
    }
  }
  return car;
}

const scene = new THREE.Scene();
scene.add(buildPanda());

new GLTFExporter().parse(
  scene,
  (glb) => {
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, Buffer.from(glb));
    console.log('Scritto ' + OUT + ' (' + Math.round(glb.byteLength / 1024) + ' KB)');
  },
  (err) => { console.error(err); process.exit(1); },
  { binary: true }
);
