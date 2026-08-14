import { hierarchy, pack } from 'd3-hierarchy';
import type { OrgNode } from '../api/orgChart';

/** Tuvalin kenar uzunlugu (SVG kullanici birimi). Cizim buna sigacak sekilde olceklenir. */
export const CANVAS = 1000;

/** Cember ile icindeki cocuklar arasindaki bosluk. */
const PADDING = 14;

export interface PackedCircle {
  node: OrgNode;
  /** Tuvalin SOL UST kosesine gore mutlak konum. */
  x: number;
  y: number;
  r: number;
  /** 0 = kapsamin tepesindeki kisi. */
  depth: number;
  /** Kendisi dahil altindaki kisi sayisi. */
  size: number;
  children: PackedCircle[];
}

/**
 * Agaci IC ICE dairelere yerlestirir.
 *
 * <p><b>Neden paketleme, force-directed degil?</b> Force-directed yerlesim AG
 * verisi icindir: dugumler birbirini iter ve kumeler olusur, ama "kim kimin
 * ustunde" bilgisi konum tesadufune kalir. Organizasyon semasi bir AGACTIR;
 * paketlemede <em>icinde olmak</em> dogrudan raporlama cizgisi demektir, yani
 * balon gorunumu elde edilirken hiyerarsi kaybolmaz.
 *
 * <p><b>Neden `d3-hierarchy`?</b> Once elle bir halka yerlesimi yazildi ve
 * calisti, ama ekranda olculdu: cemberler halkaya diziliyor, merkez bos
 * kaliyor ve tuval buyuk oranda israf oluyordu. `d3-hierarchy` (~5 KB gzip,
 * agac budanabilir) Wang'in on-zincir algoritmasi ile cok daha siki paketliyor.
 * `d3`'un tamami alinmadi: secim/gecis/eksen makinesi bu projede hic
 * kullanilmayacak ve emir kipiyle DOM'a yazan modeli React ile catisir.
 *
 * <p><b>Boyut neden yaprak sayisi?</b> Her yaprak 1 sayilir, yani bir cemberin
 * ALANI altindaki kisi sayisiyla orantilidir. Goz bir dairenin buyuklugunu
 * alanindan okur; ekip buyuklugunu capa yazmak iki kati dort kat gosterirdi.
 */
export function packForest(roots: OrgNode[]): PackedCircle | null {
  if (roots.length === 0) return null;

  // Birden fazla kok normaldir (departman baskanlari); hepsi gorunmez bir
  // cemberde toplanir. Ayri ayri cizmek daha cok yer kaplardi.
  const virtual: OrgNode = {
    id: -1,
    firstName: '',
    lastName: '',
    jobTitle: '',
    departmentName: '',
    depth: 0,
    reports: roots,
  };

  const root = hierarchy<OrgNode>(virtual, (node) => node.reports)
    .sum((node) => (node.reports.length === 0 ? 1 : 0))
    // Buyukten kucuge: buyuk daireler once yerlesince kalan bosluklar
    // kucuklerle doldurulabilir ve toplam cap kuculur.
    .sort((a, b) => (b.value ?? 0) - (a.value ?? 0));

  const laid = pack<OrgNode>().size([CANVAS, CANVAS]).padding(PADDING)(root);

  return toPacked(laid, -1);
}

type D3Node = ReturnType<ReturnType<typeof pack<OrgNode>>>;

function toPacked(circle: D3Node, depth: number): PackedCircle {
  return {
    node: circle.data,
    x: circle.x,
    y: circle.y,
    r: circle.r,
    depth,
    // Yoneticinin kendisi de sayilir: "3 kisilik ekip" ustundeki kisi dahil.
    size: circle.descendants().length,
    children: (circle.children ?? []).map((child) => toPacked(child, depth + 1)),
  };
}
