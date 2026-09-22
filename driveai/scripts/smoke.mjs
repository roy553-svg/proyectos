#!/usr/bin/env node
/**
 * DriveAI :: prueba de humo de extremo a extremo.
 *
 * Verifica lo que no puede romperse en carretera: latencia del motor
 * determinista, brevedad de seguridad vial, las 4 capas de memoria,
 * precedencia de reglas, bloqueo de comandos en movimiento y GDPR.
 *
 *   docker compose up -d
 *   node scripts/smoke.mjs                       # contra :8080
 *   DRIVEAI_URL=http://otro:9000 node scripts/smoke.mjs
 */
const B = process.env.DRIVEAI_URL ?? 'http://127.0.0.1:8080';
const D = 'e2e-driver';
/** Falla ruidosamente ante un 429 o un 5xx en vez de arrastrar `undefined`. */
async function json(res) {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText} en ${res.url}`);
  return res.json();
}
const post = (p, b) =>
  fetch(B + p, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(b),
  }).then(json);
const get = (p) => fetch(B + p).then(json);
const ask  = (m) => post('/api/assistant/message', { message: m, driverId: D });
let pass = 0, fail = 0;
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log(`  ok   ${name}`); }
  else { fail++; console.log(`  FALLA ${name}  ${detail}`); }
};

console.log('\n── 1. Motor determinista: latencia y cobertura ──');
const t0 = Date.now();
for (let i = 0; i < 30; i++) await ask('que presion tienen las llantas');
const avg = (Date.now() - t0) / 30;
check(`latencia ${avg.toFixed(1)} ms < 15 ms`, avg < 15);

for (const [q, rule] of [
  ['cuanta bateria tengo', 'battery'],
  ['como estan las llantas', 'tires'],
  ['consejos para conducir con lluvia', 'faq.rain'],
  ['hay niebla', 'faq.fog'],
  ['se revento la llanta', 'faq.tire.blowout'],
  ['el motor se calienta', 'faq.overheat'],
  ['que hora es', 'time'],
  ['estan cerrados los seguros', 'locks'],
]) {
  const r = await ask(q);
  check(`"${q}" → ${rule}`, r.rule === rule, `obtuvo ${r.rule}`);
}

console.log('\n── 2. Brevedad de seguridad vial ──');
const long = await ask('como estan las llantas');
check('≤ 3 oraciones', (long.text.match(/[.!?]/g) || []).length <= 3);
check('≤ 240 caracteres', long.text.length <= 240, `${long.text.length}`);
check('sin markdown', !/[*_`#]/.test(long.text));
check('decimal intacto (2.4 y no "2. 4")', !/\d\.\s\d/.test(long.text), long.text);

console.log('\n── 3. Memoria de 4 capas ──');
await ask('Me encanta el rock en espanol cuando manejo');
await ask('Mi casa esta en Avenida Reforma 222');
await post('/api/memory/places', { driverId: D, name: 'Trattoria Da Vinci', kind: 'restaurante', rating: 4.8, lat: 19.43, lon: -99.13 });
await post('/api/memory/preferences', { driverId: D, category: 'food', subject: 'comida italiana', origin: 'inferred', confidence: 0.62, evidence: '4 visitas' });
await new Promise(r => setTimeout(r, 800));
const snap = await get(`/api/memory?driverId=${D}&q=comida`);
check('L1 con turnos', snap.layers.L1_working_memory.length > 0);
check('L2 declarada presente', snap.layers.L2_preferences.some(p => p.origin === 'declared' && p.subject === 'rock en espanol'));
check('L2 inferida marcada', snap.layers.L2_preferences.some(p => p.origin === 'inferred' && p.confidence < 1));
check('L2 guarda evidencia', snap.layers.L2_preferences.every(p => p.evidence));
check('L3 lugar con rating', snap.layers.L3_places.some(p => p.name === 'Trattoria Da Vinci' && p.rating === 4.8));
check('L4 domicilio consolidado', snap.layers.L4_facts.some(f => f.fact_key === 'home_address'));
check('L1 podada a ≤ 20', snap.layers.L1_working_memory.length <= 20);

console.log('\n── 4. Precedencia de navegación ──');
const home = await ask('llevame a casa');
check('"a casa" → nav.home (no el restaurante)', home.rule === 'nav.home', home.rule);
check('devuelve acción de ruta', home.action?.type === 'navigate');
const food = await ask('llevame a comer');
check('"a comer" → lugar de Capa 3', ['nav.place', 'pref.food'].includes(food.rule), food.rule);
check('plural correcto ("1 vez")', !/\b1 veces\b/.test(food.text), food.text);

console.log('\n── 5. Política de seguridad del vehículo ──');
let blocked = false, climateOk = false, sawMotion = false;
for (let i = 0; i < 80; i++) {
  const s = await get('/api/vehicles/status');
  if (s.speedKph > 5) {
    sawMotion = true;
    check('UI avisa antes del toque', s.safety.lockCommandsAllowed === false);
    blocked = (await post('/api/vehicles/commands', { command: 'unlock', driverId: D })).blocked === true;
    climateOk = (await post('/api/vehicles/commands', { command: 'set_climate_temp', args: { celsius: 20 }, driverId: D })).ok === true;
    break;
  }
  await new Promise(r => setTimeout(r, 300));
}
check('coche en movimiento detectado', sawMotion);
check('seguros BLOQUEADOS en movimiento', blocked);
check('clima PERMITIDO en movimiento', climateOk);

console.log('\n── 6. Auditoría de comandos ──');
const exp = await get(`/api/memory/export?driverId=${D}`);
check('comandos auditados', exp.vehicle_command_log.length >= 2);
check('registra el bloqueo con su motivo', exp.vehicle_command_log.some(c => c.allowed === false && c.reason));

console.log('\n── 7. GDPR ──');
check('export lleva las 4 capas', ['L1_working_memory','L2_preferences','L3_places','L4_facts'].every(k => k in exp.layers));
await fetch(`${B}/api/memory?driverId=${D}`, { method: 'DELETE' });
const after = await get(`/api/memory?driverId=${D}&q=x`);
const total = Object.values(after.layers).reduce((a, v) => a + v.length, 0);
check('purga total en cascada', total === 0, `quedaron ${total}`);

console.log('\n── 8. Degradación sin errores al conductor ──');
const r404 = await fetch(B + '/ruta-inexistente');
check('ruta desconocida → cockpit, no 404', r404.status === 200);

console.log(`\n${'═'.repeat(46)}\n  ${pass} pruebas OK · ${fail} fallos\n${'═'.repeat(46)}`);
process.exit(fail ? 1 : 0);
