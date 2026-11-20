export type SnoreEvent = { id: string; nightId: string; startMs: number; durationMs: number; peakDb: number; avgDb: number; confidence: number; clipPath: string | null; starred: boolean; falsePositive: boolean };
export type SleepStage = { nightId: string; startMs: number; endMs: number; stage: 'AWAKE' | 'REM' | 'LIGHT' | 'DEEP' };
export type VitalSample = { nightId: string; tsMs: number; kind: 'SPO2' | 'HR'; value: number; source: 'RINGCONN' | 'MIBAND' };
export type Night = { id: string; sleepStartMs: number; sleepEndMs: number; tags: string[]; snoreScore: number };
export const tagOptions = ['Alkohol', 'Późny posiłek', 'Sen na plecach', 'Zatkany nos', 'Zmęczenie'];
export const stageLabels = { AWAKE: 'Czuwanie', REM: 'REM', LIGHT: 'Lekki', DEEP: 'Głęboki' };
export const nights: Night[] = [];
export const sleepStages: SleepStage[] = [];
export const snoreEvents: SnoreEvent[] = [];
export const vitalSamples: VitalSample[] = [];
let seed = 42;
const random = () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };
for (let i = 0; i < 30; i++) {
  const sleepStartMs = Date.UTC(2026, 9, 21 + i, 22, 12);
  const sleepEndMs = sleepStartMs + (i === 29 ? 456 : 430 + Math.floor(random() * 65)) * 60000;
  const id = `night-${i + 1}`;
  const alcohol = i % 5 === 1;
  nights.push({ id, sleepStartMs, sleepEndMs, tags: alcohol ? ['Alkohol', 'Sen na plecach'] : i % 3 === 0 ? ['Zatkany nos'] : ['Sen na plecach'], snoreScore: i === 29 ? 36 : Math.floor(18 + random() * 36 + (alcohol ? 12 : 0)) });
  const pattern: SleepStage['stage'][] = ['LIGHT', 'DEEP', 'LIGHT', 'REM', 'LIGHT', 'DEEP', 'LIGHT', 'REM', 'AWAKE', 'LIGHT', 'REM', 'LIGHT', 'REM'];
  pattern.forEach((stage, j) => sleepStages.push({ nightId: id, startMs: sleepStartMs + j / pattern.length * (sleepEndMs - sleepStartMs), endMs: sleepStartMs + (j + 1) / pattern.length * (sleepEndMs - sleepStartMs), stage }));
  const count = i === 29 ? 38 : Math.floor(12 + random() * 20 + (alcohol ? 14 : 0));
  const stages = sleepStages.filter(s => s.nightId === id);
  for (let j = 0; j < count; j++) {
    const rem = stages.filter(s => s.stage === 'REM');
    const target = j % 3 !== 0 ? rem[j % rem.length] : stages[j % stages.length];
    const startMs = target ? target.startMs + random() * (target.endMs - target.startMs - 300000) : sleepStartMs;
    const peakDb = i === 29 && j === 15 ? 67 : Math.floor(36 + random() * 30);
    snoreEvents.push({ id: `${id}-event-${j}`, nightId: id, startMs, durationMs: i === 29 ? (j === 15 ? 260000 : Math.round(4060000 / 37)) : Math.floor(30000 + random() * 125000), peakDb, avgDb: peakDb - 7, confidence: .82 + random() * .17, clipPath: `/mock/${id}/${j}.m4a`, starred: j === 5, falsePositive: false });
  }
  for (let t = sleepStartMs; t <= sleepEndMs; t += 300000) {
    const dip = snoreEvents.some(e => e.nightId === id && e.peakDb >= 60 && t >= e.startMs && t - e.startMs < 120000);
    vitalSamples.push({ nightId: id, tsMs: t, kind: 'SPO2', value: dip ? 88 + Math.floor(random() * 4) : 94 + Math.floor(random() * 5), source: 'RINGCONN' });
    vitalSamples.push({ nightId: id, tsMs: t, kind: 'HR', value: 52 + Math.floor(random() * 17), source: t % 600000 === 0 ? 'MIBAND' : 'RINGCONN' });
  }
}
snoreEvents.sort((a, b) => a.startMs - b.startMs);
export const latestNight = nights[nights.length - 1] as Night;
export const timeLabel = (ms: number) => new Intl.DateTimeFormat('pl-PL', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Warsaw' }).format(ms);
export const dateLabel = (ms: number) => new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'long', timeZone: 'Europe/Warsaw' }).format(ms);
export const nightStats = (night: Night, events: SnoreEvent[]) => {
  const valid = events.filter(e => e.nightId === night.id && !e.falsePositive);
  const minutes = Math.round(valid.reduce((a, e) => a + e.durationMs, 0) / 60000);
  return { minutes, percent: Math.round(minutes * 60000 / (night.sleepEndMs - night.sleepStartMs) * 100), count: valid.length, peak: Math.max(0, ...valid.map(e => e.peakDb)) };
};
export const scoreLabel = (score: number) => score < 40 ? 'Lekkie' : score < 70 ? 'Umiarkowane' : 'Silne';
