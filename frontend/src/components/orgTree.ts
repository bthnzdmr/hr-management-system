import { hierarchy, tree } from 'd3-hierarchy';
import type { OrgNode } from '../api/orgChart';

/** Iki seviye arasindaki mesafe (px). Dalin boyu budur. */
const RING = 128;

/** Isimlerin en dis halkanin disinda kapladigi yer. */
const LABEL_SPACE = 132;

/** Yaprak dairenin yaricapi; buyukler bunun uzerine biner. */
const BASE_RADIUS = 7;

/** Daire bundan buyuk olmaz; merkez butun tuvali yutmasin. */
const MAX_RADIUS = 26;

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
}

export interface TreeLink {
  id: string;
  path: string;
  depth: number;
  ancestorIds: number[];
}

export interface OrgLayout {
  nodes: TreeNode[];
  links: TreeLink[];
  /** Tuval kare; merkez tam ortasindadir. */
  size: number;
  /** Merkezdeki dugum tek bir kisi degilse gosterilecek ad. */
  hubLabel: string | null;
}

/**
 * Organizasyon semasini MERKEZDEN DAGILAN bir agac olarak yerlestirir.
 *
 * <p><b>Neden node-link, ic ice daire degil?</b> Daire paketlemede "icinde
 * olmak" raporlama cizgisidir ve bu dogru bir kodlamadir, ama gozle okunan sey
 * KUMELENMEDIR: kimin kime bagli oldugu ancak sinirlar takip edilerek cikar.
 * Node-link'te bag GORUNUR bir daldir -- iliski cikarim gerektirmez.
 *
 * <p><b>Neden radyal, soldan saga degil?</b> Soldan saga agacta genislik
 * derinlikle, yukseklik yaprak sayisiyla dogrusal buyur ve sema hizla uzun bir
 * seride donusur. Radyal yerlesimde her seviye bir HALKADIR: cevre yaricapla
 * buyudugu icin ayni tuvalde daha cok yaprak sigar ve seviye, merkeze olan
 * uzaklikla dogrudan okunur.
 *
 * <p><b>Neden Reingold-Tilford (`tree`), `cluster` degil?</b> `cluster` butun
 * yapraklari en dis halkaya hizalar; boylece dogrudan yonetici ile en alttaki
 * calisan ayni halkada gorunur ve SEVIYE bilgisi kaybolur. `tree` her dugumu
 * kendi derinliginin halkasina koyar.
 */
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

  const depth = maxDepth(root);
  const radius = Math.max(RING, depth * RING);

  const laid = tree<OrgNode>()
    .size([2 * Math.PI, radius])
    // Radyal yerlesimin klasik ayrimi: ic halkalarda cevre kisadir, bu yuzden
    // ayni aci payi disarida bol, iceride dar kalir. Derinlige bolmek bosluklari
    // butun halkalarda esitler.
    .separation((a, b) => (a.parent === b.parent ? 1 : 2) / Math.max(1, a.depth))(root);

  const centre = radius + LABEL_SPACE;
  const largest = laid.descendants().length;

  const point = (entry: { x: number; y: number }) => {
    // d3 x'i ACI, y'yi yaricap olarak verir. Aci saat 12'den baslasin diye
    // ceyrek tur geri alinir.
    const theta = entry.x - Math.PI / 2;

    return {
      x: centre + entry.y * Math.cos(theta),
      y: centre + entry.y * Math.sin(theta),
      theta,
    };
  };

  const nodes: TreeNode[] = laid.descendants().map((entry) => {
    const placed = point(entry);
    const size = entry.descendants().length;
    const isHub = entry.data.id === -1;

    return {
      node: isHub ? null : entry.data,
      x: placed.x,
      y: placed.y,
      // Alan kisi sayisiyla orantili: goz buyuklugu alandan okur, capa yazmak
      // iki kati dort kat gosterirdi.
      r: Math.min(MAX_RADIUS, BASE_RADIUS + BASE_RADIUS * 2.6 * Math.sqrt(size / largest)),
      depth: entry.depth,
      angle: (placed.theta * 180) / Math.PI,
      size,
      hasChildren: (entry.children?.length ?? 0) > 0,
      ancestorIds: chain(entry),
    };
  });

  const links: TreeLink[] = laid
    .descendants()
    .filter((entry) => entry.parent !== null)
    .map((entry) => ({
      id: `${entry.parent!.data.id}-${entry.data.id}`,
      path: branchPath(point(entry.parent!), point(entry), centre),
      depth: entry.depth,
      ancestorIds: chain(entry),
    }));

  return {
    nodes,
    links,
    size: centre * 2,
    hubLabel: single ? null : 'Organisation',
  };
}

/**
 * Iki dugumu birlestiren dal.
 *
 * <p>Duz cizgi yerine kubik bezier: kontrol noktalari ARA YARICAPTA durur, yani
 * dal once ebeveynin halkasindan disari cikar, sonra cocugun acisina yatar. Duz
 * cizgi olsaydi kalabalik bir halkada butun dallar merkeze dogru bir yildiz
 * olusturur ve hangi dalin nereye gittigi kesisimlerde kaybolurdu.
 */
function branchPath(
  from: { x: number; y: number },
  to: { x: number; y: number },
  centre: number,
): string {
  const midX = (from.x + to.x) / 2;
  const midY = (from.y + to.y) / 2;

  // Kavis merkezden UZAGA dogru degil, ara noktadan gecerek yumusatilir.
  const c1x = from.x + (midX - from.x) * 0.6 + (centre - from.x) * 0.04;
  const c1y = from.y + (midY - from.y) * 0.6 + (centre - from.y) * 0.04;
  const c2x = to.x + (midX - to.x) * 0.6 + (centre - to.x) * 0.04;
  const c2y = to.y + (midY - to.y) * 0.6 + (centre - to.y) * 0.04;

  return `M${round(from.x)},${round(from.y)}C${round(c1x)},${round(c1y)} ${round(c2x)},${round(c2y)} ${round(to.x)},${round(to.y)}`;
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
