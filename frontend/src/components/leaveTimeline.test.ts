import { describe, expect, it } from 'vitest';
import { buildRows, cellsFor, currentMonth, monthWindow, shiftMonth } from './leaveTimeline';
import type { LeaveRequest } from '../api/leaveRequests';

function leave(overrides: Partial<LeaveRequest>): LeaveRequest {
  return {
    id: 1,
    employeeId: 1,
    employeeFullName: 'Ada Lovelace',
    type: 'ANNUAL',
    status: 'APPROVED',
    startDate: '2026-08-10',
    endDate: '2026-08-14',
    days: 5,
    note: null,
    decisionNote: null,
    recordedBy: 'hr@example.com',
    decidedBy: null,
    decidedAt: null,
    createdAt: '2026-08-01T09:00:00Z',
    ...overrides,
  };
}

describe('month window', () => {
  it('ends on the real last day of the month', () => {
    expect(monthWindow('2026-08')).toMatchObject({ from: '2026-08-01', until: '2026-08-31', days: 31 });
    expect(monthWindow('2026-09')).toMatchObject({ until: '2026-09-30', days: 30 });
  });

  it('knows February in a leap year', () => {
    // Ay uzunlugu elle yazilmis bir tabloda tutulsaydi burasi kacinilmaz
    // olarak yanlis olurdu.
    expect(monthWindow('2028-02').days).toBe(29);
    expect(monthWindow('2026-02').days).toBe(28);
  });

  it('crosses the year boundary in both directions', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
  });

  it('pads a single digit month', () => {
    expect(currentMonth(new Date(2026, 2, 9))).toBe('2026-03');
  });
});

describe('rows', () => {
  it('leaves out rejected and cancelled leave', () => {
    // Takvim "kim yerinde degil" sorusunu cevaplar. Iptal edilmis bir izni
    // cizmek, takvimi yalanci kilardi.
    const rows = buildRows([
      leave({ id: 1, status: 'APPROVED' }),
      leave({ id: 2, status: 'PENDING', employeeId: 2, employeeFullName: 'Grace Hopper' }),
      leave({ id: 3, status: 'REJECTED', employeeId: 3, employeeFullName: 'Alan Turing' }),
      leave({ id: 4, status: 'CANCELLED', employeeId: 4, employeeFullName: 'Barbara Liskov' }),
    ], '2026-08');

    expect(rows.map((row) => row.employeeName)).toEqual(['Ada Lovelace', 'Grace Hopper']);
  });

  it('clips a leave that starts before the month', () => {
    const [row] = buildRows([leave({ startDate: '2026-07-28', endDate: '2026-08-03' })], '2026-08');

    expect(row.bars[0]).toMatchObject({ startDay: 1, span: 3, clippedStart: true, clippedEnd: false });
  });

  it('clips a leave that runs past the end of the month', () => {
    const [row] = buildRows([leave({ startDate: '2026-08-30', endDate: '2026-09-04' })], '2026-08');

    expect(row.bars[0]).toMatchObject({ startDay: 30, span: 2, clippedStart: false, clippedEnd: true });
  });

  it('spans the whole month when the month sits inside the leave', () => {
    const [row] = buildRows([leave({ startDate: '2026-07-20', endDate: '2026-09-10' })], '2026-08');

    expect(row.bars[0]).toMatchObject({ startDay: 1, span: 31, clippedStart: true, clippedEnd: true });
  });

  it('counts a single day leave as one day', () => {
    const [row] = buildRows([leave({ startDate: '2026-08-17', endDate: '2026-08-17' })], '2026-08');

    expect(row.bars[0]).toMatchObject({ startDay: 17, span: 1 });
  });

  it('groups every leave of one person into a single row, in date order', () => {
    const rows = buildRows([
      leave({ id: 2, startDate: '2026-08-20', endDate: '2026-08-21' }),
      leave({ id: 1, startDate: '2026-08-03', endDate: '2026-08-04' }),
    ], '2026-08');

    expect(rows).toHaveLength(1);
    expect(rows[0].bars.map((bar) => bar.startDay)).toEqual([3, 20]);
  });

  it('orders people by name so a row keeps its place between months', () => {
    const rows = buildRows([
      leave({ employeeId: 2, employeeFullName: 'Zeynep Yilmaz' }),
      leave({ employeeId: 1, employeeFullName: 'Ada Lovelace' }),
    ], '2026-08');

    expect(rows.map((row) => row.employeeName)).toEqual(['Ada Lovelace', 'Zeynep Yilmaz']);
  });
});

describe('row cells', () => {
  /**
   * ASIL DEGISMEZ: colSpan toplami ay uzunlugunu vermezse tablo kayar ve
   * baslikta 17 yazan sutunun altinda baska bir gunun seridi durur.
   */
  const total = (cells: ReturnType<typeof cellsFor>) =>
    cells.reduce((sum, cell) => sum + ('gap' in cell ? cell.gap : cell.bar.span), 0);

  it('always fills exactly the month', () => {
    const cases: LeaveRequest[][] = [
      [],
      [leave({ startDate: '2026-08-01', endDate: '2026-08-31' })],
      [leave({ startDate: '2026-08-01', endDate: '2026-08-02' })],
      [leave({ startDate: '2026-08-31', endDate: '2026-08-31' })],
      // Ayin SONDAN BIR ONCEKI gununde biten izin: geriye tam bir gunluk
      // bosluk kalir ve kapanis kosulundaki `<=` tam burada gerekir. Bu vaka
      // olmadan `<` yazmak testleri kirmiyordu.
      [leave({ startDate: '2026-08-28', endDate: '2026-08-30' })],
      [
        leave({ id: 1, startDate: '2026-08-03', endDate: '2026-08-05' }),
        leave({ id: 2, startDate: '2026-08-06', endDate: '2026-08-07' }),
        leave({ id: 3, startDate: '2026-08-20', endDate: '2026-08-28' }),
      ],
    ];

    cases.forEach((leaves) => {
      const rows = buildRows(leaves, '2026-08');
      const bars = rows[0]?.bars ?? [];

      expect(total(cellsFor(bars, 31))).toBe(31);
    });
  });

  it('puts a gap before a leave that does not start on the first', () => {
    const [row] = buildRows([leave({ startDate: '2026-08-05', endDate: '2026-08-06' })], '2026-08');
    const cells = cellsFor(row.bars, 31);

    expect(cells[0]).toEqual({ gap: 4 });
    expect(cells[1]).toHaveProperty('bar');
  });

  it('does not open a gap when a leave starts on the first', () => {
    const [row] = buildRows([leave({ startDate: '2026-08-01', endDate: '2026-08-02' })], '2026-08');

    expect(cellsFor(row.bars, 31)[0]).toHaveProperty('bar');
  });
});
