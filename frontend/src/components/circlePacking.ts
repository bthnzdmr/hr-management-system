import type { OrgNode } from '../api/orgChart';

/** Yaprak dugumun yaricapi. Bas harfler icin yeterli. */
const LEAF_RADIUS = 26;

/** Bir dugum ile ic cemberi arasindaki bosluk. */
const PADDING = 10;

/** Halkaya yerlesirken komsular arasinda birakilan aci payi. */
const ANGULAR_SLACK = 0.12;

export interface PackedCircle {
  node: OrgNode;
  /** Ebeveynin merkezine gore konum. */
  x: number;
  y: number;
  r: number;
  depth: number;
  children: PackedCircle[];
}

/**
 * Agaci IC ICE dairelere yerlestirir.
 *
 * <p><b>Neden paketleme, force-directed degil?</b> Force-directed yerlesim AG
 * verisi icindir: dugumler birbirini iter ve kumeler olusur, ama "kim kimin
 * ustunde" bilgisi konum tesadufune kalir. Organizasyon semasi bir AGACTIR;
 * paketlemede <em>icinde olmak</em> dogrudan raporlama cizgisi demektir, yani
 * hiyerarsi kaybolmaz.
 *
 * <p><b>Algoritma (asagidan yukari):</b> once cocuklar yerlestirilir ve
 * yariçaplari bilinir hale gelir, sonra ebeveyn onlari cevreleyecek kadar
 * buyutulur. Tersi mumkun degil -- ebeveynin ne kadar yer gerektirdigi,
 * cocuklarin ne kadar yer kapladigina baglidir.
 *
 * <p>Kutuphane eklenmedi: ihtiyacimiz olan sey birkac trigonometri satiri ve
 * bunun icin zaten Math var. Sparkline ve yonetici yuku gorselinde de ayni
 * karar verildi.
 */
export function packTree(node: OrgNode, depth = 0): PackedCircle {
  if (node.reports.length === 0) {
    return { node, x: 0, y: 0, r: LEAF_RADIUS, depth, children: [] };
  }

  // Cocuklar once yerlesir: ebeveynin boyu onlara bagli.
  const children = node.reports.map((report) => packTree(report, depth + 1));

  // Buyukten kucuge: buyuk daireler once yerlesince kalan bosluklar kucuklerle
  // doldurulabilir ve toplam cap kuculur.
  children.sort((a, b) => b.r - a.r);

  if (children.length === 1) {
    // Tek cocuk halkaya dizilmez, merkeze konur: halka olsaydi ebeveyn
    // gereksiz yere iki kat buyurdu.
    const only = children[0];
    only.x = 0;
    only.y = 0;
    return { node, x: 0, y: 0, r: only.r + PADDING * 2, depth, children };
  }

  const ringRadius = findRingRadius(children.map((c) => c.r));

  // Aci payi her cocugun kapladigi genisligi ORANTILI dagitir: buyuk daire
  // daha genis bir yay kaplar. Esit acilarla dagitmak buyukleri ust uste
  // bindirirdi.
  const spans = children.map((c) => 2 * Math.asin(Math.min(1, c.r / ringRadius)));
  const total = spans.reduce((sum, span) => sum + span, 0);
  const gap = Math.max(0, (2 * Math.PI - total) / children.length);

  let angle = -Math.PI / 2;
  for (let i = 0; i < children.length; i += 1) {
    const child = children[i];
    angle += spans[i] / 2;
    child.x = Math.cos(angle) * ringRadius;
    child.y = Math.sin(angle) * ringRadius;
    angle += spans[i] / 2 + gap;
  }

  const outer = Math.max(...children.map((c) => ringRadius + c.r));

  return { node, x: 0, y: 0, r: outer + PADDING, depth, children };
}

/**
 * Cocuklarin cakismadigi EN KUCUK halka yaricapi.
 *
 * Bir daire merkeze `d` uzakliktaysa halkada `2*asin(r/d)` kadar aci kaplar.
 * Toplam 2*PI'yi asmamali. `d` buyudukce her cocugun acisi kucullur, yani
 * fonksiyon monotondur ve IKILI ARAMA ile cozulebilir -- kapali bir formul
 * yazmaya calismak burada gereksiz karmasiklik olurdu.
 */
function findRingRadius(radii: number[]): number {
  const largest = Math.max(...radii);

  let low = largest;
  let high = largest * radii.length + 1;

  for (let step = 0; step < 40; step += 1) {
    const mid = (low + high) / 2;
    const used = radii.reduce(
      (sum, r) => sum + 2 * Math.asin(Math.min(1, r / mid)),
      0,
    );

    if (used + ANGULAR_SLACK * radii.length <= 2 * Math.PI) {
      high = mid;
    } else {
      low = mid;
    }
  }

  return high;
}

/**
 * Birden fazla kok icin sanal bir kapsayici.
 *
 * Yoneticisi olmayan birden fazla kisi normaldir (departman baskanlari) ve bu
 * bir veri eksikligi DEGILDIR -- panelde bir kez ogrenilmisti. Hepsini tek bir
 * gorunmez cemberde toplamak, ayri ayri cizmekten daha az yer kaplar.
 */
export function packForest(roots: OrgNode[]): PackedCircle | null {
  if (roots.length === 0) return null;

  const virtual: OrgNode = {
    id: -1,
    firstName: '',
    lastName: '',
    jobTitle: '',
    departmentName: '',
    depth: 0,
    reports: roots,
  };

  const packed = packTree(virtual, -1);
  // Sanal kok cizilmez; cocuklarinin derinligi 0'dan baslamali.
  return packed;
}
