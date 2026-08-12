import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Sparkline } from './Sparkline';

function pointsOf(label: string): [number, number][] {
  const polyline = screen.getByRole('img', { name: label }).querySelector('polyline');
  return (polyline?.getAttribute('points') ?? '')
    .split(' ')
    .map((pair) => pair.split(',').map(Number) as [number, number]);
}

describe('Sparkline', () => {
  it('carries an accessible name because the curve is data, not decoration', () => {
    render(<Sparkline values={[1, 4, 2]} color="#C68465" label="Monthly hires, ending at 2" />);

    expect(screen.getByRole('img', { name: 'Monthly hires, ending at 2' })).toBeInTheDocument();
  });

  it('puts the highest value at the top', () => {
    // SVG'de y asagi dogru buyur; en yuksek deger EN KUCUK y'yi almali.
    // Ters cevrilseydi grafik veriyi tam tersine anlatirdi.
    render(<Sparkline values={[0, 10, 5]} color="#C68465" label="trend" height={20} />);

    const [first, peak, middle] = pointsOf('trend');
    expect(peak[1]).toBeLessThan(middle[1]);
    expect(middle[1]).toBeLessThan(first[1]);
  });

  it('draws a flat series through the middle instead of dividing by zero', () => {
    // max === min oldugunda (max - min) sifirdir. Korunmasaydi her y degeri
    // NaN olur ve polyline hic cizilmezdi -- "degisim yok" da bir bulgudur.
    render(<Sparkline values={[3, 3, 3]} color="#C68465" label="flat" height={20} />);

    for (const [, y] of pointsOf('flat')) {
      expect(Number.isNaN(y)).toBe(false);
      expect(y).toBe(20);
    }
  });

  it('draws nothing when a single point cannot form a line', () => {
    const { container } = render(<Sparkline values={[7]} color="#C68465" label="single" />);

    expect(container).toBeEmptyDOMElement();
  });
});
