import { hierarchy, tree } from 'd3-hierarchy';
import type { OrgNode } from '../api/orgChart';

/** Iki seviye arasindaki EN KUCUK mesafe (px). Kalabalik halkalarda buyur. */
const RING = 186;

/** Iki komsu dugum arasinda birakilan en kucuk aciklik. */
const NODE_GAP = 12;

/** En dis halkanin disinda birakilan bosluk. */
const LABEL_SPACE = 92;

/** Yaprak dairenin yaricapi; buyukler bunun uzerine biner. */
const BASE_RADIUS = 26;

/** Daire bundan buyuk olmaz; merkez butun tuvali yutmasin. */
const MAX_RADIUS = 78;

export interface TreeNode {
  node: OrgNode | null;
  /** Tuval koordinati (merkez tuvalin ortasidir). */
  x: number;
  y: number;
  r: number;
  /** 0 = merkez. */
  depth: number;
  /** Dairenin merkeze gore acisi (derece, 0 = saat 3 yonu). */
  angle: number;
  /** Kendisi dahil altindaki kisi sayisi. */
  size: number;
  hasChildren: boolean;
  /** Merkezden kendisine kadar olan zincir; uzerine gelince yol vurgulanir. */
  ancestorIds: number[];
  /** Ayni ebeveynin kacinci cocugu (1 tabanli) ve kardes sayisi. */
  position: number;
  siblings: number;
}

export interface TreeLink {
  id: string;
  path: string;
  depth: number;
  /** Dalin ucundaki alt agacin kisi sayisi; kalinlik buna gore incelir. */
  size: number;
  ancestorIds: number[];
}

export interface OrgLayout {
  nodes: TreeNode[];
  links: TreeLink[];
  /** Her seviyenin yaricapi; arkaya soluk halka cizmek icin. */
  rings: number[];
  /** En kalabalik alt agacin kisi sayisi; dal kalinligi buna gore olceklenir. */
  largest: number;
  /** Tuval kare; merkez tam ortasindadir. */
  size: number;
  /** Merkez tek bir kisi mi? Degilse orada kurumun isareti durur. */
  hub: boolean;
}

/** Organizasyon semasini MERKEZDEN DAGILAN bir agac olarak yerlestirir. */
export function layoutTree(roots: OrgNode[]): OrgLayout | null {
  if (roots.length === 0) return null;

  // Tek kok varsa merkezde O durur. Birden fazla kok normaldir (departman
  // baskanlari) ve o zaman merkeze kapsamin kendisi konur -- gorunmez birakmak
  // dallari bosluktan cikiyor gibi gosterirdi.
  const single = roots.length === 1 ? roots[0] : null;

  const virtual: OrgNode = {
    id: -1,
    firstName: '',
    lastName: '',
    jobTitle: '',
    departmentName: '',
    depth: 0,
    reports: roots,
  };

  const root = hierarchy<OrgNode>(single ?? virtual, (node) => node.reports);

  const totals = subtreeTotals(single ?? virtual);

  const depth = maxDepth(root);
  const radius = Math.max(RING, depth * ringSpacing(root, totals)) ;

  const laid = tree<OrgNode>()
    .size([2 * Math.PI, radius])
    // Radyal yerlesimin klasik ayrimi: ic halkalarda cevre kisadir, bu yuzden
    // ayni aci payi disarida bol, iceride dar kalir. Derinlige bolmek bosluklari
    // butun halkalarda esitler.
    .separation((a, b) => (a.parent === b.parent ? 1 : 2) / Math.max(1, a.depth))(root);

  const centre = radius + LABEL_SPACE;
  const largest = Math.max(...[...totals.values()]);

  const point = (entry: { x: number; y: number }) => {
    // d3 x'i ACI, y'yi yaricap olarak verir. Aci saat 12'den baslasin diye
    // ceyrek tur geri alinir.
    const theta = entry.x - Math.PI / 2;
    const placed = polar(theta, entry.y, centre);

    return { ...placed, theta, radius: entry.y };
  };

  const nodes: TreeNode[] = laid.descendants().map((entry) => {
    const placed = point(entry);
    const size = totals.get(entry.data.id) ?? 1;
    const isHub = entry.data.id === -1;

    return {
      node: isHub ? null : entry.data,
      x: placed.x,
      y: placed.y,
      // Alan kisi sayisiyla orantili: goz buyuklugu alandan okur, capa yazmak
      // iki kati dort kat gosterirdi.
      r: radiusFor(size, largest),
      depth: entry.depth,
      angle: (placed.theta * 180) / Math.PI,
      size,
      hasChildren: entry.data.reports.length > 0,
      ancestorIds: chain(entry),
      // Ekran okuyucu SVG'de DOM ic icelikten seviye cikaramaz; konum ve
      // kardes sayisi acikca bildirilmeli.
      position: (entry.parent?.children?.indexOf(entry) ?? 0) + 1,
      siblings: entry.parent?.children?.length ?? 1,
    };
  });

  const links: TreeLink[] = laid
    .descendants()
    .filter((entry) => entry.parent !== null)
    .map((entry) => ({
      id: `${entry.parent!.data.id}-${entry.data.id}`,
      path: branchPath(
        { angle: point(entry.parent!).theta, radius: point(entry.parent!).radius },
        { angle: point(entry).theta, radius: point(entry).radius },
        centre,
      ),
      depth: entry.depth,
      size: totals.get(entry.data.id) ?? 1,
      ancestorIds: chain(entry),
    }));

  return {
    nodes,
    links,
    // Seviye halkalari: merkezden sonraki her derinlik icin bir yaricap.
    rings: Array.from({ length: depth }, (_, index) => ((index + 1) * radius) / depth),
    largest,
    size: centre * 2,
    hub: single === null,
  };
}

/** Iki dugumu birlestiren dal. */
function branchPath(
  from: { angle: number; radius: number },
  to: { angle: number; radius: number },
  centre: number,
): string {
  const middle = (from.radius + to.radius) / 2;

  const start = polar(from.angle, from.radius, centre);
  const control1 = polar(from.angle, middle, centre);
  const control2 = polar(to.angle, middle, centre);
  const end = polar(to.angle, to.radius, centre);

  return `M${round(start.x)},${round(start.y)}C${round(control1.x)},${round(control1.y)} ${round(control2.x)},${round(control2.y)} ${round(end.x)},${round(end.y)}`;
}

/** Kutupsal koordinati tuval koordinatina cevirir. */
function polar(angle: number, radius: number, centre: number) {
  return { x: centre + radius * Math.cos(angle), y: centre + radius * Math.sin(angle) };
}

function round(value: number) {
  return Math.round(value * 10) / 10;
}

function maxDepth(root: { descendants: () => { depth: number }[] }) {
  return Math.max(...root.descendants().map((entry) => entry.depth));
}

/** Merkezden bu dugume kadar olan kisi kimlikleri; gorunmez merkez haric. */
function chain(entry: { data: OrgNode; parent: unknown }): number[] {
  const ids: number[] = [];

  let current = entry as { data: OrgNode; parent: typeof entry | null } | null;

  while (current) {
    if (current.data.id !== -1) ids.push(current.data.id);
    current = current.parent as typeof current;
  }

  return ids;
}

/** Her dugumun altindaki gercek kisi sayisi. */
function subtreeTotals(root: OrgNode): Map<number, number> {
  const totals = new Map<number, number>();

  const visit = (node: OrgNode): number => {
    const total = 1 + node.reports.reduce((sum, report) => sum + visit(report), 0);
    totals.set(node.id, total);

    return total;
  };

  visit(root);

  return totals;
}

/** Halkalar arasi mesafe. */
function ringSpacing(
  root: { descendants: () => { depth: number; data: OrgNode }[] },
  totals: Map<number, number>,
): number {
  const largest = Math.max(...[...totals.values()]);
  const perDepth = new Map<number, { count: number; widest: number }>();

  for (const entry of root.descendants()) {
    if (entry.depth === 0) continue;

    const size = totals.get(entry.data.id) ?? 1;
    const r = radiusFor(size, largest);
    const seen = perDepth.get(entry.depth) ?? { count: 0, widest: 0 };

    perDepth.set(entry.depth, {
      count: seen.count + 1,
      widest: Math.max(seen.widest, r),
    });
  }

  let spacing = RING;

  for (const [depth, { count, widest }] of perDepth) {
    // Cevre = 2*PI*(depth*spacing); her dugum 2r + bosluk kadar yay ister.
    const needed = (count * (2 * widest + NODE_GAP)) / (2 * Math.PI * depth);
    spacing = Math.max(spacing, needed);
  }

  return spacing;
}

/** Bir dugumun yaricapi; alan altindaki kisi sayisiyla orantilidir. */
function radiusFor(size: number, largest: number) {
  return Math.min(MAX_RADIUS, BASE_RADIUS + BASE_RADIUS * 2.6 * Math.sqrt(size / largest));
}
