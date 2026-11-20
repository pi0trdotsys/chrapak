import { describe, expect, it } from 'vitest';
import { nights, snoreEvents, sleepStages, vitalSamples, nightStats, latestNight, scoreLabel } from '@/data/mock';
describe('Chrapak mock domain', () => {
 it('generates exactly 30 nights', () => { expect(nights).toHaveLength(30); expect(new Set(nights.map(n => n.id)).size).toBe(30); });
 it('matches the reference morning report', () => { expect(nightStats(latestNight, snoreEvents)).toEqual({ minutes: 72, percent: 16, count: 38, peak: 67 }); });
 it('contains occasional SpO2 dips between 88 and 91', () => { expect(vitalSamples.some(v => v.kind === 'SPO2' && v.value >= 88 && v.value <= 91)).toBe(true); expect(vitalSamples.filter(v => v.kind === 'SPO2').every(v => v.value >= 88 && v.value <= 98)).toBe(true); });
 it('keeps heart rate in the requested 52–68 bpm range', () => { const hr = vitalSamples.filter(v => v.kind === 'HR'); expect(hr.length).toBeGreaterThan(0); expect(hr.every(v => v.value >= 52 && v.value <= 68)).toBe(true); });
 it('includes all four sleep stages with valid intervals', () => { expect(new Set(sleepStages.map(s => s.stage))).toEqual(new Set(['AWAKE','REM','LIGHT','DEEP'])); expect(sleepStages.every(s => s.endMs > s.startMs)).toBe(true); });
 it('keeps snore scores within 0–100', () => { expect(nights.every(n => n.snoreScore >= 0 && n.snoreScore <= 100)).toBe(true); });
 it('excludes false positives from statistics', () => { const ev = snoreEvents.filter(e => e.nightId === latestNight.id); const marked = ev.map(e => ({ ...e, falsePositive: true })); expect(nightStats(latestNight, marked)).toEqual({ minutes: 0, percent: 0, count: 0, peak: 0 }); });
 it('labels score severity', () => { expect(scoreLabel(36)).toBe('Lekkie'); expect(scoreLabel(50)).toBe('Umiarkowane'); expect(scoreLabel(80)).toBe('Silne'); });
});
