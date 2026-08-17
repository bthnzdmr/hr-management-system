import { describe, expect, it } from 'vitest';
import { formatDateTime, formatDay } from './formatDate';

describe('formatDay', () => {
  it('puts the day first, then the month, then the year', () => {
    // Ham ISO ("2026-08-17") yil ONCE gelir ve okunmasi zordur.
    expect(formatDay('2026-08-17')).toBe('17-08-2026');
  });

  it('does not shift a date-only value by a day', () => {
    // new Date('2026-01-01') bu dizgeyi UTC GECE YARISI sayar; negatif ofsetli
    // bir saat diliminde ekranda 31 Aralik gorunurdu ve izinler bir gun
    // kayardi. Dizge parcalanarak bicimlendiriliyor, Date hic kurulmuyor.
    expect(formatDay('2026-01-01')).toBe('01-01-2026');
    expect(formatDay('2026-12-31')).toBe('31-12-2026');
  });

  it('does not depend on the machine locale', () => {
    // `Intl` kullanilsaydi ayraç ve sira makineye gore degisirdi; ayni ekran
    // her makinede farkli basar ve test edilemezdi.
    expect(formatDay('2026-03-04')).toBe('04-03-2026');
  });

  it('shows the raw value rather than inventing one', () => {
    expect(formatDay('bozuk')).toBe('bozuk');
  });
});

describe('formatDateTime', () => {
  it('keeps the time next to the date', () => {
    const shown = formatDateTime('2026-08-17T09:15:30Z');

    // Damga bir ANDIR ve yerel dilime cevrilir; yeterince batidaki bir
    // makinede GUN de kayar. Iddia bu yuzden degere degil BICIME bakiyor --
    // gune bakan bir iddia, calistigi makineye gore dogru veya yanlis olurdu.
    expect(shown).toMatch(/^\d{2}-\d{2}-\d{4} \d{2}:\d{2}$/);
  });

  it('falls back to the raw timestamp instead of printing Invalid Date', () => {
    expect(formatDateTime('bu bir tarih degil')).toBe('bu bir tarih degil');
  });
});
