import { describe, expect, it } from 'vitest';
import { packForest, packTree } from './circlePacking';
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

/** Iki kardes cakisiyor mu? Merkezler arasi uzaklik, yariçaplar toplamindan kucukse evet. */
function overlaps(a: PackedCircle, b: PackedCircle) {
  const distance = Math.hypot(a.x - b.x, a.y - b.y);
  return distance + EPSILON < a.r + b.r;
}

/** Agacin her seviyesinde iki degismezi dogrular. */
function assertInvariants(circle: PackedCircle) {
  const { children } = circle;

  for (const child of children) {
    // 1) Cocuk ebeveynin ICINDE kalmali. Tasarsa "icinde olmak" raporlama
    //    cizgisi olmaktan cikardi -- gorselin tasidigi TEK bilgi budur.
    expect(Math.hypot(child.x, child.y) + child.r).toBeLessThanOrEqual(circle.r + EPSILON);
  }

  for (let i = 0; i < children.length; i += 1) {
    for (let j = i + 1; j < children.length; j += 1) {
      // 2) Kardesler cakismamali: cakisan iki daire, birinin digerinin
      //    astiymis gibi okunmasina yol acardi.
      expect(overlaps(children[i], children[j])).toBe(false);
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

    assertInvariants(packTree(tree));
  });

  it('holds even when one manager carries a crowd', () => {
    // Kalabalik ekip halkayi zorlar: yariçap yeterince buyumezse cocuklar
    // ust uste biner. Ikili arama tam olarak bunu engellemek icin var.
    const crowd = Array.from({ length: 25 }).map((_, index) => node(index + 2));

    assertInvariants(packTree(node(1, crowd)));
  });

  it('does not waste space on a manager with a single report', () => {
    // Tek cocuk halkaya dizilseydi ebeveyn gereksiz yere iki kat buyurdu.
    const single = packTree(node(1, [node(2)]));
    const leaf = packTree(node(3));

    expect(single.children[0].x).toBe(0);
    expect(single.children[0].y).toBe(0);
    expect(single.r).toBeLessThan(leaf.r * 2);
  });

  it('grows the parent when the team grows', () => {
    const small = packTree(node(1, [node(2), node(3)]));
    const large = packTree(node(1, [node(2), node(3), node(4), node(5), node(6)]));

    expect(large.r).toBeGreaterThan(small.r);
  });

  it('gathers several roots into one invisible circle', () => {
    // Yoneticisi olmayan birden fazla kisi normaldir (departman baskanlari).
    const packed = packForest([node(1), node(2), node(3)]);

    expect(packed).not.toBeNull();
    expect(packed?.children).toHaveLength(3);
    // Sanal kok cizilmedigi icin cocuklari 0. seviyeden baslar.
    expect(packed?.children[0].depth).toBe(0);
    assertInvariants(packed as PackedCircle);
  });

  it('returns nothing when there is nobody to draw', () => {
    expect(packForest([])).toBeNull();
  });
});
