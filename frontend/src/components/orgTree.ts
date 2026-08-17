import { hierarchy, tree } from 'd3-hierarchy';
import type { OrgNode } from '../api/orgChart';

/**
 * Gorunmez merkezin kimligi.
 *
 * SIFIR, cunku eksi degerler ALINMIS: `groupByDepartment` departman
 * dugumlerine `-(index+1)` veriyor, yani ilk departmanin id'si -1. Merkez de
 * -1 kullandigi surece o departman merkez saniliyor ve ekranda birbirinin
 * ayni IKI merkez balonu cikiyordu -- departmanin kendisi de kayboluyordu.
 *
 * Sifir guvenli: personel id'leri 1'den baslayan bir diziden geliyor,
 * sentetik dugumler ise eksi tarafta.
 */
const HUB_ID = 0;

/** Iki seviye arasindaki EN KUCUK mesafe (tuval birimi). Kalabalikta buyur. */
const RING = 200;

/** Iki komsu daire arasinda birakilan en kucuk aciklik. */
const NODE_GAP = 18;

/** Yaprak dairenin yaricapi. */
const LEAF_RADIUS = 24;

/**
 * En kalabalik dugumun yaricapi.
 *
 * Onceden 78'di ve en buyuk daire tuvalin dortte birini yiyordu: sema bir
 * hiyerarsi degil, birkac buyuk leke gibi okunuyordu. Ekip buyuklugu hala
 * kodlaniyor -- yalnizca artik BASKIN degil.
 */
const MAX_RADIUS = 46;

/** Isim yazisinin boyu (tuval birimi) ve altindaki unvan. */
export const NAME_SIZE = 26;
export const TITLE_SIZE = 20;

/** Daire ile isim arasi, ve iki satir arasi. */
export const LABEL_GAP = 10;
export const LINE_GAP = 4;

/** Harf basina yaklasik genislik orani; kirpma bununla hesaplanir. */
export const CHAR_WIDTH = 0.55;

/**
 * Dugumlerin kendi yerinde salinim genligi (tuval birimi).
 *
 * Deger EKRAN hareketine gore secilir, tuval birimine gore degil: SVG
 * kullanici birimi mutlak bir olcu degildir, anlamini viewBox ile kazanir.
 * 0.64 olcekte 5 birim ~3.2 px eder.
 *
 * Genligi buyutmenin bedeli var -- LABEL_PAD buna bagli ve pay buyudukce
 * etiket sigmayan dugum sayisi artiyor. Hareketi FARK EDILIR kilan sey zaten
 * genlik degil HIZ: ayni mesafeyi yarı surede almak, bedeli sifir olan
 * kaldiractir. Bu yuzden genlik olculu kaldi, sure kisaldi.
 */
export const DRIFT_AMPLITUDE = 5;

/**
 * Iki komsu etiket arasinda birakilan aciklik.
 *
 * Salinim genligine BAGLI, cunku iki dugum birbirine dogru ayni anda
 * salinabilir: en kotu durumda aralarindaki mesafe 2 x genlik kadar kapanir.
 * Sabit bir sayi yazsaydik genlik degistigi gun cakismasizlik garantisi
 * sessizce yalan soylemeye baslardi.
 */
const LABEL_PAD = 2 * DRIFT_AMPLITUDE + 6;

/** Etiket bundan genis olmaz; uzun isim kirpilir. */
const LABEL_MAX_WIDTH = 300;

/**
 * Daraltilmis etiketin alt siniri (~8 harf).
 *
 * Ic halkadaki bir dugumun tam boy etiketi, DIS halkanin dairelerine carpiyor
 * ve "ya tam boy ya hic" kuralinda o kisi etiketsiz kaliyordu -- olcumde
 * kaybedenlerden biri bir departman baskaniydi. Kirpilmis bir isim, hic isim
 * olmamasindan iyidir; tam ad zaten <title>'da ve panelde duruyor.
 */
const MIN_LABEL_WIDTH = 120;

/** Etiket blogunun yuksekligi: isim + unvan. */
const LABEL_HEIGHT = NAME_SIZE + LINE_GAP + TITLE_SIZE;

/**
 * En dis halkanin disinda birakilan bosluk.
 *
 * Etiket dairenin YANINA yaziliyor, altina degil; dolayisiyla pay etiketin
 * yuksekligini degil GENISLIGINI karsilamali.
 */
const OUTER_MARGIN = MAX_RADIUS + LABEL_GAP + LABEL_MAX_WIDTH;

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
  /** Etiketin nereye ve ne genislikte yazilacagi; yer yoksa null. */
  label: LabelBox | null;
}

export interface LabelBox {
  /** Metnin cizilecegi nokta. */
  x: number;
  /** Isim satirinin baseline'i; unvan bir satir asagida. */
  y: number;
  anchor: 'start' | 'middle' | 'end';
  /** Metnin kirpilacagi genislik. */
  width: number;
}

interface Rect {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

export interface TreeLink {
  id: string;
  path: string;
  depth: number;
  /** Dalin ucundaki alt agacin kisi sayisi. */
  size: number;
  ancestorIds: number[];
}

export interface OrgLayout {
  nodes: TreeNode[];
  links: TreeLink[];
  /** Tuval kare; merkez tam ortasindadir. Yerlestirme bunun icinde kalir. */
  size: number;
  /**
   * Cizimin GERCEKTEN doldurdugu dikdortgen.
   *
   * Kare tuval, agacin doldurmadigi bos sektorleri de tasiyor: canli veride
   * en dis halka 360 derecenin yalnizca 220'sine yayiliyor. Kareyi oldugu gibi
   * gostermek o bosluga yer ayirip cizimi kucultuyordu; viewBox icerige
   * kirpiliyor ve ayni kap icinde her sey buyuyor.
   */
  view: Rect;
  /** Merkez tek bir kisi mi? Degilse orada kapsamin isareti durur. */
  hub: boolean;
}

/**
 * Organizasyon semasini MERKEZDEN DAGILAN bir agac olarak yerlestirir.
 *
 * `alwaysHub` verildiginde merkez, tek kok olsa bile KAPSAMIN kendisi olur.
 * Bir departman secildiginde bu sart: aksi halde merkezde departmanin isareti
 * yalnizca birden fazla baskani olan departmanlarda gorunur, tek baskanlilarda
 * oraya o kisi oturur ve ayni ekran departmandan departmana farkli davranir.
 */
export function layoutTree(roots: OrgNode[], alwaysHub = false): OrgLayout | null {
  if (roots.length === 0) return null;

  // Butun organizasyonda tek kok varsa merkezde O durur -- genel mudurun
  // yerine soyut bir isaret koymak bilgi kaybi olurdu.
  const single = !alwaysHub && roots.length === 1 ? roots[0] : null;

  const virtual: OrgNode = {
    id: HUB_ID,
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
  const radius = Math.max(RING, depth * ringSpacing(root, totals));

  const laid = tree<OrgNode>()
    .size([2 * Math.PI, radius])
    // Radyal yerlesimin klasik ayrimi: ic halkalarda cevre kisadir, bu yuzden
    // ayni aci payi disarida bol, iceride dar kalir. Derinlige bolmek bosluklari
    // butun halkalarda esitler.
    .separation((a, b) => (a.parent === b.parent ? 1 : 2) / Math.max(1, a.depth))(root);

  const centre = radius + OUTER_MARGIN;
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
    const isHub = entry.data.id === HUB_ID;

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
      // Gercek deger asagida, butun dugumler yerlestikten sonra yaziliyor.
      label: null,
    };
  });

  placeLabels(nodes, centre * 2);

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
    size: centre * 2,
    view: contentBox(nodes),
    hub: single === null,
  };
}

/**
 * Cizilen her seyi saran en kucuk dikdortgen.
 *
 * Paya salinim genligi dahil: dugumler yerlerinde salindigi icin sinirdaki bir
 * etiket, pay birakilmazsa hareketin yarisinda kirpilirdi.
 */
function contentBox(nodes: TreeNode[]): Rect {
  const pad = DRIFT_AMPLITUDE + 12;

  let left = Infinity;
  let right = -Infinity;
  let top = Infinity;
  let bottom = -Infinity;

  const cover = (box: Rect) => {
    left = Math.min(left, box.left);
    right = Math.max(right, box.right);
    top = Math.min(top, box.top);
    bottom = Math.max(bottom, box.bottom);
  };

  for (const entry of nodes) {
    cover({
      left: entry.x - entry.r,
      right: entry.x + entry.r,
      top: entry.y - entry.r,
      bottom: entry.y + entry.r,
    });

    if (!entry.label) continue;

    const { x, width, anchor, y } = entry.label;
    const start = anchor === 'start' ? x : anchor === 'end' ? x - width : x - width / 2;

    cover({
      left: start,
      right: start + width,
      top: y - NAME_SIZE,
      bottom: y + LINE_GAP + TITLE_SIZE,
    });
  }

  return { left: left - pad, right: right + pad, top: top - pad, bottom: bottom + pad };
}

/**
 * Etiketleri yerlestirir.
 *
 * Ilk tasarim etiketi dairenin ALTINA koyuyordu ve halka araligini etiket
 * GENISLIGINE gore buyutuyordu. Olculdu ve iki yonden birden yanlisti:
 *
 * 1. Aralik ortalama yaydan hesaplaniyordu, oysa tidy tree dugumleri esit
 *    dagitmaz -- gercek en dar cift ortalamanin yarisi kadardi.
 * 2. Daha temeli: alta yazilan bir etiket komsusundan YATAYDA ayrilmak
 *    zorundadir ve bir isim, tasidigi daireden cok daha genistir. Halkayi o
 *    kadar buyutmek tuvali buyutuyor, tuval buyuyunce de yazi kuculuyordu --
 *    butce arttikca kazanc doymaya gidiyor.
 *
 * Etiket artik dairenin YANINA yaziliyor. Halkanin solunda ve saginda komsular
 * DIKEYDE ayrilir ve etiket yalnizca kendi yuksekligi kadar yer ister; genislik
 * ise halkanin disindaki bos alana tasar. Kalan catisma tepede ve dipte olusur,
 * onu da yon denemesi cozer.
 *
 * Sira onemli: once ust seviyeler ve kalabalik ekipler yerlesir, yani yer
 * darsa etiketini kaybeden taraf her zaman en az onemli yaprak olur.
 */
function placeLabels(nodes: TreeNode[], canvas: number) {
  const circles: Rect[] = nodes.map((entry) => ({
    left: entry.x - entry.r,
    right: entry.x + entry.r,
    top: entry.y - entry.r,
    bottom: entry.y + entry.r,
  }));

  const taken: Rect[] = [];

  const order = nodes
    .map((entry, index) => ({ entry, index }))
    .filter((item) => item.entry.node !== null)
    .sort((a, b) => a.entry.depth - b.entry.depth || b.entry.size - a.entry.size);

  for (const { entry, index } of order) {
    const node = entry.node!;
    const wanted = Math.min(
      LABEL_MAX_WIDTH,
      Math.max(
        `${node.firstName} ${node.lastName}`.length * NAME_SIZE * CHAR_WIDTH,
        node.jobTitle.length * TITLE_SIZE * CHAR_WIDTH,
      ),
    );

    const theta = (entry.angle * Math.PI) / 180;
    const others = circles.filter((_, i) => i !== index);

    const free = (option: { rect: Rect }) => within(option.rect, canvas)
      && !others.some((circle) => hits(option.rect, circle))
      && !taken.some((rect) => hits(option.rect, rect));

    // Once TAM boy, butun yonlerde denenir; ancak hicbiri sigmazsa daraltilir.
    // Sira boyle: genislik okunabilirligi belirler, yon yalnizca duzeni.
    const fit = widths(wanted)
      .flatMap((width) => candidates(entry, width, theta))
      .find(free);

    if (!fit) continue;

    entry.label = fit.box;
    taken.push(fit.rect);
  }
}

/** Denenecek genislikler, genisten dara. */
function widths(wanted: number): number[] {
  const steps = [wanted, wanted * 0.72, MIN_LABEL_WIDTH]
    .filter((width) => width <= wanted && width >= MIN_LABEL_WIDTH);

  // Isim zaten alt sinirdan kisaysa daraltmanin anlami yok: tek aday kendisi.
  if (steps.length === 0) return [wanted];

  return [...new Set(steps)].sort((a, b) => b - a);
}

/**
 * Dort yerlesim adayi, DISA dogru olandan baslayarak.
 *
 * Siralama esik yerine skalar carpimla yapiliyor: her yonun disari birim
 * vektoruyle carpimi, o yonun "ne kadar disari" oldugudur. Esik yazsaydik
 * (`|cos| > 0.3` gibi) sinirdaki dugumler keyfi bir tarafa duserdi.
 */
function candidates(entry: TreeNode, width: number, theta: number) {
  const { x, y, r } = entry;
  const offset = r + LABEL_GAP;
  const half = LABEL_HEIGHT / 2;

  const options = [
    {
      score: Math.cos(theta),
      box: { x: x + offset, y: y - half + NAME_SIZE * 0.78, anchor: 'start' as const, width },
      rect: { left: x + offset, right: x + offset + width, top: y - half, bottom: y + half },
    },
    {
      score: -Math.cos(theta),
      box: { x: x - offset, y: y - half + NAME_SIZE * 0.78, anchor: 'end' as const, width },
      rect: { left: x - offset - width, right: x - offset, top: y - half, bottom: y + half },
    },
    {
      score: Math.sin(theta),
      box: { x, y: y + offset + NAME_SIZE * 0.78, anchor: 'middle' as const, width },
      rect: {
        left: x - width / 2, right: x + width / 2, top: y + offset, bottom: y + offset + LABEL_HEIGHT,
      },
    },
    {
      score: -Math.sin(theta),
      box: { x, y: y - offset - LABEL_HEIGHT + NAME_SIZE * 0.78, anchor: 'middle' as const, width },
      rect: {
        left: x - width / 2, right: x + width / 2, top: y - offset - LABEL_HEIGHT, bottom: y - offset,
      },
    },
  ];

  return options.sort((a, b) => b.score - a.score);
}

/** Iki dikdortgen kesisiyor mu? Aralarinda LABEL_PAD kadar pay birakilir. */
function hits(a: Rect, b: Rect) {
  return a.left - LABEL_PAD < b.right
    && b.left < a.right + LABEL_PAD
    && a.top - LABEL_PAD < b.bottom
    && b.top < a.bottom + LABEL_PAD;
}

function within(rect: Rect, canvas: number) {
  return rect.left >= 0 && rect.top >= 0 && rect.right <= canvas && rect.bottom <= canvas;
}

/** Bu genislige sigan en uzun on ek; sigmiyorsa uc nokta eklenir. */
export function truncate(text: string, width: number, fontSize: number): string {
  const fits = Math.floor(width / (fontSize * CHAR_WIDTH));

  if (fits >= text.length) return text;
  if (fits < 2) return '';

  return `${text.slice(0, fits - 1).trimEnd()}…`;
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
    if (current.data.id !== HUB_ID) ids.push(current.data.id);
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

/**
 * Halkalar arasi mesafe.
 *
 * Yalnizca DAIRELERIN cakismamasini garanti eder. Etiketler bu hesaba
 * girmiyor: onlari halka araligiyla cozmek denendi ve tuvali oyle buyutuyordu
 * ki yazi ekranda kuculuyordu. Etiket catismasi artik yerlestirme asamasinda,
 * yon secerek cozuluyor.
 */
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
  return LEAF_RADIUS + (MAX_RADIUS - LEAF_RADIUS) * Math.sqrt(size / largest);
}
