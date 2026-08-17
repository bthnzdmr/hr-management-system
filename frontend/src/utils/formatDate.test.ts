import { describe, expect, it } from 'vitest';
import { formatDateTime, formatDay } from './formatDate';

describe('formatDay', () => {
  it('puts the day first and names the month', () => {
    // Ham ISO ("2026-08-17") yil ONCE gelir ve okunmasi zordur. Ay adiyla
    // yazilinca 03/04 belirsizligi de hic olusmuyor.
    expect(formatDay('2026-08-17')).toBe('17 Aug 2026');
  });

  it('does not shift a date-only value by a day', () => {
    // new Date('2026-01-01') bu dizgeyi UTC GECE YARISI sayar; negatif ofsetli
    // bir saat diliminde ekranda 31 Dec gorunurdu ve izinler bir gun kayardi.
    expect(formatDay('2026-01-01')).toBe('01 Jan 2026');
    expect(formatDay('2026-12-31')).toBe('31 Dec 2026');
  });

  it('shows the raw value rather than inventing one', () => {
    expect(formatDay('bozuk')).toBe('bozuk');
  });
});

describe('formatDateTime', () => {
  it('keeps the time next to the date', () => {
    const shown = formatDateTime('2026-08-17T09:15:30Z');

    expect(shown).toContain('17 Aug 2026');
    // Saat yerel dilime cevrilir; hangi saat oldugu makineye bagli, VARLIGI
    // degil -- iddia da bu yuzden bicime bakiyor.
    expect(shown).toMatch(/\d{2}:\d{2}/);
  });

  it('falls back to the raw timestamp instead of printing Invalid Date', () => {
    expect(formatDateTime('bu bir tarih degil')).toBe('bu bir tarih degil');
  });
});
