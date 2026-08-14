import { describe, expect, it } from 'vitest';
import { CANVAS, packForest } from './circlePacking';
import type { PackedCircle } from './circlePacking';
import type { OrgNode } from '../api/orgChart';

function node(id: number, reports: OrgNode[] = []): OrgNode {
  return {
    id,
    firstName: 'Test',
    lastName: `P${id}`,
    jobTitle: 'Engineer',
    departmentName: 'Sales',
    depth: 1,
    reports,
  };
}

/** Kayan nokta karsilastirmasi icin tolerans; tam esitlik beklenemez. */
const EPSILON = 0.001;

/** Agacin her seviyesinde iki degismezi dogrular. */
function assertInvariants(circle: PackedCircle) {
  const { children } = circle;

  for (const child of children) {
    // 1) Cocuk ebeveynin ICINDE kalmali. Tasarsa "icinde olmak" raporlama
    //    cizgisi olmaktan cikardi -- gorselin tasidigi TEK bilgi budur.
    const distance = Math.hypot(child.x - circle.x, child.y - circle.y);
    expect(distance + child.r).toBeLessThanOrEqual(circle.r + EPSILON);
  }

  for (let i = 0; i < children.length; i += 1) {
    for (let j = i + 1; j < children.length; j += 1) {
      // 2) Kardesler cakismamali: cakisan iki daire, birinin digerinin
      //    astiymis gibi okunmasina yol acardi.
      const a = children[i];
      const b = children[j];
      const distance = Math.hypot(a.x - b.x, a.y - b.y);
      expect(distance + EPSILON).toBeGreaterThanOrEqual(a.r + b.r);
    }

    assertInvariants(children[i]);
  }
}

describe('circlePacking', () => {
  it('keeps every child inside its parent and apart from its siblings', () => {
    const tree = node(1, [
      node(2, [node(5), node(6), node(7)]),
      node(3, [node(8, [node(10), node(11)])]),
      node(4),
      node(9),
    ]);

    assertInvariants(packForest([tree]) as PackedCircle);
  });

  it('holds even when one manager carries a crowd', () => {
    const crowd = Array.from({ length: 25 }).map((_, index) => node(index + 2));

    assertInvariants(packForest([node(1, crowd)]) as PackedCircle);
  });

  it('fills the canvas instead of leaving it mostly empty', () => {
    // Elle yazilan ilk yerlesim halka tabanliydi ve merkezi bos birakiyordu;
    // ekranda olculdu, tuvalin buyuk kismi israf oluyordu. Bu iddia o gerilemeyi
    // yakalar: en dis cember tuvale gercekten oturmali.
    const tree = node(1, [
      node(2, [node(5), node(6), node(7)]),
      node(3, [node(8), node(9)]),
      node(4),
    ]);

    const packed = packForest([tree]) as PackedCircle;

    expect(packed.r * 2).toBeGreaterThan(CANVAS * 0.98);
  });

  it('sizes a circle by the number of people inside it, not by depth', () => {
    // Alan kisi sayisiyla orantili: goz bir dairenin buyuklugunu alanindan
    // okur, capa yazmak iki kati dort kat gosterirdi.
    const packed = packForest([
      node(1, [node(2), node(3), node(4), node(5)]),
      node(6, [node(7)]),
    ]) as PackedCircle;

    const [big, small] = packed.children;

    expect(big.size).toBe(5);
    expect(small.size).toBe(2);
    expect(big.r).toBeGreaterThan(small.r);
  });

  it('gathers several roots into one invisible circle', () => {
    // Yoneticisi olmayan birden fazla kisi normaldir (departman baskanlari).
    const packed = packForest([node(1), node(2), node(3)]);

    expect(packed?.children).toHaveLength(3);
    // Sanal kok cizilmedigi icin cocuklari 0. seviyeden baslar.
    expect(packed?.children[0].depth).toBe(0);
    assertInvariants(packed as PackedCircle);
  });

  it('returns nothing when there is nobody to draw', () => {
    expect(packForest([])).toBeNull();
  });
});
