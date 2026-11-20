// Renders one SVG mockup per theme straight from the tokens in src/styles.css,
// so the README previews never drift from the real palette.
// Usage: node scripts/generate-theme-svgs.mjs
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const css = readFileSync(join(root, "src/styles.css"), "utf8");
const outDir = join(root, "docs/themes");
mkdirSync(outDir, { recursive: true });

const THEMES = [
  { id: "polnoc", name: "Północ", selector: ':root, [data-theme="polnoc"]' },
  { id: "zar", name: "Żar", selector: '[data-theme="zar"]' },
  { id: "las", name: "Las", selector: '[data-theme="las"]' },
  { id: "oled", name: "Czerń OLED", selector: '[data-theme="oled"]' },
  { id: "dzien", name: "Dzień", selector: '[data-theme="dzien"]' },
];

function tokensFor(selector) {
  const start = css.indexOf(selector + " {");
  if (start < 0) throw new Error(`Theme block not found: ${selector}`);
  const body = css.slice(start, css.indexOf("}", start));
  const tokens = {};
  for (const m of body.matchAll(/--([\w-]+):\s*([^;]+);/g)) tokens[m[1]] = m[2].trim();
  return tokens;
}

function oklchToHex(value) {
  const m = value.match(/oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)/);
  if (!m) throw new Error(`Unsupported colour: ${value}`);
  const [L, C, h] = [Number(m[1]), Number(m[2]), (Number(m[3]) * Math.PI) / 180];
  const a = C * Math.cos(h);
  const b = C * Math.sin(h);
  const l_ = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m_ = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s_ = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const lin = [
    4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
    -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
    -0.0041960863 * l_ - 0.7034186147 * m_ + 1.707614701 * s_,
  ];
  const gamma = (x) => {
    const c = Math.min(1, Math.max(0, x));
    return c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055;
  };
  return "#" + lin.map((x) => Math.round(gamma(x) * 255).toString(16).padStart(2, "0")).join("");
}

// Deterministic pseudo-random so output is stable between runs.
function rng(seed) {
  let s = seed;
  return () => ((s = (s * 16807) % 2147483647) - 1) / 2147483646;
}

function render(theme) {
  const t = Object.fromEntries(
    Object.entries(tokensFor(theme.selector)).map(([k, v]) => [k, v.startsWith("oklch") ? oklchToHex(v) : v]),
  );
  const W = 360;
  const H = 800;
  const rand = rng(7);

  // Timeline geometry
  const tx = 36, tw = 288;
  const stagesOrder = ["LIGHT", "DEEP", "LIGHT", "REM", "LIGHT", "DEEP", "LIGHT", "REM", "AWAKE", "LIGHT", "REM", "LIGHT", "REM"];
  const stageY = { AWAKE: 0, REM: 12, LIGHT: 24, DEEP: 36 };
  const stageFill = { AWAKE: t["sleep-awake"], REM: t.primary, LIGHT: t["sleep-light"], DEEP: t["sleep-deep"] };
  const segW = tw / stagesOrder.length;
  const stageRects = stagesOrder
    .map((s, i) => `<rect x="${(tx + i * segW).toFixed(1)}" y="${stageY[s]}" width="${(segW - 1).toFixed(1)}" height="9" rx="2" fill="${stageFill[s]}"/>`)
    .join("");
  const bars = Array.from({ length: 30 }, (_, i) => {
    const loud = rand() > 0.72;
    const h = 8 + rand() * (loud ? 34 : 22);
    const x = tx + 4 + i * ((tw - 8) / 29);
    return `<rect x="${x.toFixed(1)}" y="${(46 - h).toFixed(1)}" width="4" height="${h.toFixed(1)}" rx="2" fill="${loud ? t.amber : t.primary}" opacity="${loud ? 1 : 0.7}"/>`;
  }).join("");
  const spo2 = Array.from({ length: 49 }, (_, i) => {
    const dip = [11, 12, 27, 38].includes(i);
    const y = dip ? 22 + rand() * 4 : 5 + rand() * 6;
    return `${(tx + (i / 48) * tw).toFixed(1)},${y.toFixed(1)}`;
  }).join(" ");
  const hr = Array.from({ length: 49 }, (_, i) => `${(tx + (i / 48) * tw).toFixed(1)},${(8 + rand() * 18).toFixed(1)}`).join(" ");

  const card = (x, y, w, h, extra = "") => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="${t.card}" stroke="${t.border}" ${extra}/>`;
  const stat = (x, y, label, value, unit, color) =>
    `${card(x, y, 144, 62)}<text x="${x + 14}" y="${y + 22}" font-size="10" fill="${t["muted-foreground"]}">${label}</text>` +
    `<text x="${x + 14}" y="${y + 50}" font-size="22" font-weight="500" fill="${color}">${value}<tspan font-size="11" fill="${t["muted-foreground"]}" dx="4">${unit}</tspan></text>`;

  const tabs = [
    ["Noc", 60, false],
    ["Raport", 150, true],
    ["Trendy", 240, false],
    ["Ustawienia", 320, false],
  ]
    .map(([label, x, active]) => {
      const c = active ? t.primary : t["muted-foreground"];
      return `<circle cx="${x}" cy="${H - 80}" r="6" fill="none" stroke="${c}" stroke-width="1.6"/><text x="${x}" y="${H - 58}" font-size="9.5" text-anchor="middle" fill="${c}">${label}</text>`;
    })
    .join("");

  const swatches = [
    ["background", t.background],
    ["card", t.card],
    ["primary", t.primary],
    ["teal", t.teal],
    ["amber", t.amber],
    ["deep", t["sleep-deep"]],
    ["light", t["sleep-light"]],
    ["awake", t["sleep-awake"]],
  ]
    .map(([n, c], i) => `<g transform="translate(${28 + i * 41},0)"><circle cx="14" cy="14" r="13" fill="${c}" stroke="${t.border}"/></g>`)
    .join("");

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" font-family="Manrope, -apple-system, 'Segoe UI', Roboto, sans-serif" role="img" aria-label="Motyw ${theme.name}: ekran porannego raportu">
<title>Chrapak, motyw ${theme.name}</title>
<rect x="0.5" y="0.5" width="${W - 1}" height="${H - 1}" rx="30" fill="${t.background}" stroke="${t.border}"/>
<rect x="22" y="30" width="30" height="34" rx="10" fill="${t.card}" stroke="${t.border}"/>
<g fill="${t.primary}"><rect x="29" y="42" width="2.4" height="10" rx="1.2"/><rect x="34" y="37" width="2.4" height="20" rx="1.2"/><rect x="39" y="44" width="2.4" height="6" rx="1.2"/><rect x="44" y="40" width="2.4" height="14" rx="1.2"/></g>
<text x="62" y="55" font-size="22" font-weight="700" fill="${t.foreground}">chrapak<tspan fill="${t.primary}">.</tspan></text>
<circle cx="258" cy="48" r="3" fill="${t.teal}"/><text x="266" y="52" font-size="10" fill="${t["muted-foreground"]}">Spokojnej nocy</text>
<text x="22" y="100" font-size="8.5" letter-spacing="1.6" fill="${t["muted-foreground"]}">TWÓJ PORANNY RAPORT</text>
<text x="22" y="128" font-size="21" font-weight="700" fill="${t.foreground}">Dzień dobry, spokojnie.</text>
${card(22, 146, 316, 42)}
<text x="180" y="172" font-size="12" text-anchor="middle" fill="${t.foreground}">19 listopada <tspan fill="${t["muted-foreground"]}">2026</tspan></text>
<circle cx="76" cy="248" r="44" fill="none" stroke="${t.border}" stroke-width="6"/>
<circle cx="76" cy="248" r="44" fill="none" stroke="${t.primary}" stroke-width="6" stroke-linecap="round" stroke-dasharray="${(0.36 * 276.46).toFixed(1)} 276.46" transform="rotate(-90 76 248)"/>
<text x="76" y="258" font-size="30" text-anchor="middle" fill="${t.foreground}">36</text>
<text x="76" y="274" font-size="8.5" text-anchor="middle" fill="${t["muted-foreground"]}">na 100</text>
<rect x="142" y="212" width="110" height="22" rx="11" fill="${t["soft-primary"]}"/>
<circle cx="156" cy="223" r="3" fill="${t.primary}"/><text x="164" y="227" font-size="9.5" fill="${t.foreground}">Lekkie chrapanie</text>
<text x="142" y="256" font-size="14.5" font-weight="700" fill="${t.foreground}">Całkiem spokojna noc</text>
<text x="142" y="274" font-size="9.5" fill="${t["muted-foreground"]}">Chrapanie zajęło 16% Twojej nocy.</text>
${stat(22, 316, "Czas chrapania", "1 h", "12 min", t.foreground)}
${stat(194, 316, "Część nocy", "16", "%", t.foreground)}
${stat(22, 388, "Liczba epizodów", "38", "", t.foreground)}
${stat(194, 388, "Najgłośniej", "67", "dB", t.amber)}
<text x="22" y="486" font-size="14" font-weight="700" fill="${t.foreground}">Twoja noc, w szczegółach</text>
${card(22, 500, 316, 182)}
<g transform="translate(0,516)">${stageRects}</g>
<line x1="${tx}" x2="${tx + tw}" y1="564" y2="564" stroke="${t.border}"/>
<g transform="translate(0,568)">${bars}</g>
<line x1="${tx}" x2="${tx + tw}" y1="618" y2="618" stroke="${t.border}"/>
<line x1="${tx}" x2="${tx + tw}" y1="650" y2="650" stroke="${t.border}"/>
<g transform="translate(0,620)"><polyline points="${spo2}" fill="none" stroke="${t.teal}" stroke-width="1.4" stroke-linejoin="round"/></g>
<g transform="translate(0,652)" opacity="0.9"><polyline points="${hr}" fill="none" stroke="${t.heart}" stroke-width="1" stroke-linejoin="round" transform="scale(1,0.9)"/></g>
<rect x="0" y="${H - 104}" width="${W}" height="104" rx="30" fill="${t.card}" stroke="${t.border}"/>
<rect x="0.5" y="${H - 104}" width="${W - 1}" height="30" fill="${t.card}"/>
${tabs}
<g transform="translate(0,${H - 36})">${swatches}</g>
</svg>
`;
}

for (const theme of THEMES) {
  writeFileSync(join(outDir, `${theme.id}.svg`), render(theme));
  console.log(`wrote docs/themes/${theme.id}.svg`);
}
