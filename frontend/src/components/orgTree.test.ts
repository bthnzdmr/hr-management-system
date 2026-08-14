import { describe, expect, it } from 'vitest';
import { layoutTree } from './orgTree';
import type { OrgLayout, TreeNode } from './orgTree';
import type { OrgNode } from '../api/orgChart';

/** Hicbir dugum kapali degil. */
const NONE = new Set<number>();

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

/** Merkezden olan uzaklik; seviye halkasi bununla dogrulanir. */
function radius(layout: OrgLayout, entry: TreeNode) {
  const centre = layout.size / 2;

  return Math.hypot(entry.x - centre, entry.y - centre);
}

function byId(layout: OrgLayout, id: number) {
  return layout.nodes.find((entry) => entry.node?.id === id) as TreeNode;
}

describe('orgTree', () => {
  it('puts a single root in the centre and everyone else around it', () => {
    const layout = layoutTree([node(1, [node(2), node(3)])], NONE) as OrgLayout;

    expect(radius(layout, byId(layout, 1))).toBeCloseTo(0);
    expect(radius(layout, byId(layout, 2))).toBeGreaterThan(0);
  });

  it('places each level on its own ring', () => {
    // Seviye, merkeze olan UZAKLIKLA okunur. `cluster` yerlesimi butun
    // yapraklari en dis halkaya hizalardi ve dogrudan bir yonetici ile en
    // alttaki calisan ayni halkada gorunurdu -- seviye bilgisi kaybolurdu.
    const layout = layoutTree([
      node(1, [
        node(2, [node(4, [node(5)])]),
        node(3),
      ]),
    ], NONE) as OrgLayout;

    const first = radius(layout, byId(layout, 2));
    const second = radius(layout, byId(layout, 4));
    const third = radius(layout, byId(layout, 5));

    expect(radius(layout, byId(layout, 3))).toBeCloseTo(first);
    expect(second).toBeGreaterThan(first);
    expect(third).toBeGreaterThan(second);
  });

  it('draws one branch per reporting line, and none from the hub', () => {
    // Birden fazla kok normaldir (departman baskanlari) ve gorunmez merkezden
    // cikan dallar cizilseydi olmayan bir raporlama cizgisi gosterilirdi.
    const layout = layoutTree([node(1, [node(2)]), node(3)], NONE) as OrgLayout;

    expect(layout.hubLabel).toBe('Organisation');
    expect(layout.links).toHaveLength(3);
    expect(layout.links.map((link) => link.id)).toContain('-1-1');
  });

  it('names the centre only when it is not a person', () => {
    const single = layoutTree([node(1, [node(2)])], NONE) as OrgLayout;
    const many = layoutTree([node(1), node(2)], NONE) as OrgLayout;

    expect(single.hubLabel).toBeNull();
    expect(many.hubLabel).toBe('Organisation');
  });

  it('keeps every node inside the canvas', () => {
    // Tuval yaricapa gore olceklenir; derin bir agac disari tasarsa en dis
    // halka kirpilirdi.
    const deep = node(1, [node(2, [node(3, [node(4, [node(5)])])])]);
    const layout = layoutTree([deep], NONE) as OrgLayout;

    for (const entry of layout.nodes) {
      expect(entry.x - entry.r).toBeGreaterThanOrEqual(0);
      expect(entry.y - entry.r).toBeGreaterThanOrEqual(0);
      expect(entry.x + entry.r).toBeLessThanOrEqual(layout.size);
      expect(entry.y + entry.r).toBeLessThanOrEqual(layout.size);
    }
  });

  it('does not let siblings land on the same spot', () => {
    // Kalabalik bir ekipte aci payi yetmezse dugumler ust uste biner ve iki
    // kisi tek bir daire gibi gorunur.
    const crowd = Array.from({ length: 25 }).map((_, index) => node(index + 2));
    const layout = layoutTree([node(1, crowd)], NONE) as OrgLayout;

    const leaves = layout.nodes.filter((entry) => !entry.hasChildren);

    for (let i = 0; i < leaves.length; i += 1) {
      for (let j = i + 1; j < leaves.length; j += 1) {
        const distance = Math.hypot(leaves[i].x - leaves[j].x, leaves[i].y - leaves[j].y);
        expect(distance).toBeGreaterThan(leaves[i].r + leaves[j].r);
      }
    }
  });

  it('sizes a circle by the number of people under it', () => {
    // Alan kisi sayisiyla orantili: goz buyuklugu alandan okur.
    const layout = layoutTree([
      node(1, [
        node(2, [node(4), node(5), node(6), node(7)]),
        node(3),
      ]),
    ], NONE) as OrgLayout;

    expect(byId(layout, 2).size).toBe(5);
    expect(byId(layout, 3).size).toBe(1);
    expect(byId(layout, 2).r).toBeGreaterThan(byId(layout, 3).r);
  });

  it('carries the whole chain back to the centre on every node', () => {
    // Uzerine gelince YOL vurgulanir; zincir dugumun kendisinde durmasaydi
    // her hover'da agac yeniden yurunurdu.
    const layout = layoutTree([node(1, [node(2, [node(3)])])], NONE) as OrgLayout;

    expect(byId(layout, 3).ancestorIds).toEqual([3, 2, 1]);
  });

  it('returns nothing when there is nobody to draw', () => {
    expect(layoutTree([], NONE)).toBeNull();
  });
});

describe('collapsing', () => {
  it('leaves out the children of a closed node', () => {
    const tree = node(1, [node(2, [node(3), node(4)])]);

    const open = layoutTree([tree], NONE) as OrgLayout;
    const shut = layoutTree([tree], new Set([2])) as OrgLayout;

    expect(open.nodes).toHaveLength(4);
    expect(shut.nodes).toHaveLength(2);
  });

  it('still reports the real team size of a closed node', () => {
    // Kapali bir yoneticinin dairesi kucuk gorunseydi "kimse yok" gibi
    // okunurdu; oysa gizlenen kisiler hala oradadir.
    const tree = node(1, [node(2, [node(3), node(4)])]);

    const shut = layoutTree([tree], new Set([2])) as OrgLayout;
    const closed = shut.nodes.find((entry) => entry.node?.id === 2) as TreeNode;

    expect(closed.size).toBe(3);
    expect(closed.hidden).toBe(2);
    expect(closed.collapsed).toBe(true);
  });

  it('keeps a closed node clickable so it can be opened again', () => {
    const shut = layoutTree([node(1, [node(2)])], new Set([1])) as OrgLayout;

    expect(shut.nodes[0].hasChildren).toBe(true);
  });

  it('does not call a childless node collapsed', () => {
    // Kapali isaretlemek onu tiklanabilir gosterirdi ve tiklama hicbir sey
    // yapmazdi.
    const leafShut = layoutTree([node(1, [node(2)])], new Set([2])) as OrgLayout;
    const leaf = leafShut.nodes.find((entry) => entry.node?.id === 2) as TreeNode;

    expect(leaf.collapsed).toBe(false);
    expect(leaf.hasChildren).toBe(false);
  });
});

describe('branch geometry', () => {
  /** Yol dizesindeki sayi ciftlerini cikarir. */
  function points(path: string) {
    return (path.match(/-?\d+(\.\d+)?,-?\d+(\.\d+)?/g) ?? [])
      .map((pair) => pair.split(',').map(Number) as [number, number]);
  }

  it('puts both control points on the ring between parent and child', () => {
    // `d3.linkRadial`in geometrisi: kontrol noktalari ORTA YARICAPTA durur.
    // Boylece dal her iki ucta yaricap dogrultusunda cikip girer ve acisal
    // gecisi halkalar arasindaki BOS bantta yapar. Duz bir kiris ebeveynin
    // kendi halkasini keserdi -- kardes alt agaclarin durdugu yeri.
    const layout = layoutTree([node(1, [node(2), node(3)])], NONE) as OrgLayout;
    const centre = layout.size / 2;

    const [start, control1, control2, end] = points(layout.links[0].path);

    const radiusOf = ([x, y]: [number, number]) => Math.hypot(x - centre, y - centre);
    const middle = (radiusOf(start) + radiusOf(end)) / 2;

    expect(radiusOf(control1)).toBeCloseTo(middle, 1);
    expect(radiusOf(control2)).toBeCloseTo(middle, 1);
  });

  it('starts and ends exactly on the two nodes it joins', () => {
    // Bag dugumun uzerinde bitmezse cizim ile yapi birbirini tutmaz.
    const layout = layoutTree([node(1, [node(2)])], NONE) as OrgLayout;
    const [start, , , end] = points(layout.links[0].path);

    const parent = layout.nodes.find((entry) => entry.node?.id === 1) as TreeNode;
    const child = layout.nodes.find((entry) => entry.node?.id === 2) as TreeNode;

    expect(start[0]).toBeCloseTo(parent.x, 1);
    expect(start[1]).toBeCloseTo(parent.y, 1);
    expect(end[0]).toBeCloseTo(child.x, 1);
    expect(end[1]).toBeCloseTo(child.y, 1);
  });
});
