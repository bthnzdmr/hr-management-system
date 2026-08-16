import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useBusyRows } from './useBusyRows';

describe('useBusyRows', () => {
  it('keeps each row independent', () => {
    // Olculen kusur: tek yuvali bayrakta once biten islem, DIGER satirin
    // dugmelerini ucus halindeyken tekrar aciyordu -- cift gonderim mumkundu.
    const { result } = renderHook(() => useBusyRows());

    act(() => {
      result.current.start(1);
      result.current.start(2);
    });
    expect(result.current.isBusy(1)).toBe(true);
    expect(result.current.isBusy(2)).toBe(true);

    act(() => result.current.finish(1));

    expect(result.current.isBusy(1)).toBe(false);
    // 2 hala ucusta: birinci satirin bitmesi ikinciyi serbest birakmamali.
    expect(result.current.isBusy(2)).toBe(true);
  });

  it('treats a missing id as idle', () => {
    const { result } = renderHook(() => useBusyRows());

    expect(result.current.isBusy(null)).toBe(false);
    expect(result.current.isBusy(undefined)).toBe(false);
  });
});
