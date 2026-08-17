import { describe, expect, it } from 'vitest';
import {
  CHAR_WIDTH, DRIFT_AMPLITUDE, LINE_GAP, NAME_SIZE, TITLE_SIZE, layoutTree, truncate,
} from './orgTree';
import type { OrgLayout, TreeNode } from './orgTree';
import type { OrgNode } from '../api/orgChart';

/**
 * Etiketler her dugumde YAZILI oldugu icin yeni bir degismez dogdu: iki
 * komsunun etiketi cakismamali. Cakisan iki etiket okunmaz -- ustelik
 * cakisma testlerden kacar, cunku dugumler cakismiyor.
 *
 * Olcum gercekci veriyle yapilir: 33 kisi, dort seviye ve GERCEK uzunlukta
 * isimler/unvanlar. Kisa sahte adlarla ("Test P7") olculseydi hicbir zaman
 * cakisma gorulmez ve kural hic sinanmamis olurdu.
 */

let next = 1;

function person(name: string, title: string, reports: OrgNode[] = []): OrgNode {
  const [firstName, ...rest] = name.split(' ');

  return {
    id: next++,
    firstName,
    lastName: rest.join(' '),
    jobTitle: title,
    departmentName: 'Engineering',
    depth: 0,
    reports,
  };
}

/** Tohumlanan organizasyona benzeyen bir agac: 33 kisi, dort seviye. */
function realisticOrg(): OrgNode[] {
  next = 1;

  return [
    person('Ada Lovelace', 'Chief Executive Officer', [
      person('Mehmet Eren Demirbulut', 'Chief Technology Officer', [
        person('Bora Ataysan', 'Senior Software Engineer', [
          person('Emir Beyhatun', 'Software Engineer'),
          person('Mevlana Ayas', 'Software Engineer'),
          person('Mustafa Ertugrul Er', 'Senior Software Engineer'),
        ]),
        person('Deniz Hamzaoglu', 'QA Specialist', [
          person('Beyda Akdemir', 'QA Specialist'),
          person('Kadir Ejder Colak', 'Test Automation Lead'),
        ]),
        person('Tugce Numanoglu', 'Data Scientist', [
          person('Kerem Demirtas', 'Data Scientist'),
          person('Ahmet Yurdakul', 'Data Analyst'),
          person('Hasan Civi', 'Senior Data Analyst'),
        ]),
      ]),
      person('Furkan Anarat', 'Chief Growth Officer', [
        person('Irem Arik', 'Marketing Manager', [
          person('Enes Saltan', 'Performance Marketing Manager'),
          person('Metehan Gultekin', 'Senior Performance Manager'),
          person('Serkan Ertem', 'Lead Marketing Artist'),
        ]),
        person('Melih Elmas', '3D Marketing', [
          person('Caglar Ozen', '3D Artist'),
          person('Erman Sales', 'Senior Animator'),
        ]),
      ]),
      person('Barkin Basaran', 'Chief Product Officer', [
        person('Okay Irdelp', 'Lead Product Manager', [
          person('Ecem Altun Kimilli', 'Product Manager'),
          person('Firat Gundogdu', 'Product Manager'),
          person('Oguzcan Ece', 'Product Manager'),
        ]),
        person('Alaz Gursan', 'Product Level Designer', [
          person('Ozcan Olguner', 'Product Level Designer'),
        ]),
      ]),
      person('Fuat Coskun', 'Co-Founder & CTO', [
        person('Emre Bugdayci', 'Senior Data Analyst'),
        person('Mert Ozturk', 'Data Scientist'),
        person('Ata Berk Cinetci', 'Data Analyst'),
      ]),
      person('Husnu Akin Bebayigit', 'Investor Advisor', [
        person('Phil Sanderson', 'Board Member'),
        person('Mithat Madra', 'Co-Founder'),
        person('M. Sinan Ayyorgun', 'Snr Illustrator'),
      ]),
    ]),
  ];
}

/** Etiketin ekranda gercekten kapladigi dikdortgen. */
function labelBox(entry: TreeNode) {
  const node = entry.node!;
  const box = entry.label!;
  const name = truncate(`${node.firstName} ${node.lastName}`, box.width, NAME_SIZE);
  const title = truncate(node.jobTitle, box.width, TITLE_SIZE);

  // Cizilen genislik, ayrilan yer degil KIRPILMIS metnin genisligidir.
  const width = Math.max(
    name.length * NAME_SIZE * CHAR_WIDTH,
    title.length * TITLE_SIZE * CHAR_WIDTH,
  );

  const left = box.anchor === 'start'
    ? box.x
    : box.anchor === 'end' ? box.x - width : box.x - width / 2;
  const top = box.y - NAME_SIZE * 0.78;

  return { left, right: left + width, top, bottom: top + NAME_SIZE + LINE_GAP + TITLE_SIZE };
}

function labelled(layout: OrgLayout) {
  return layout.nodes.filter((entry) => entry.node !== null && entry.label !== null);
}

function overlaps(a: ReturnType<typeof labelBox>, b: ReturnType<typeof labelBox>) {
  return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
}

describe('organisation map labels', () => {
  it('never lets two labels overlap, even at the worst moment of the drift', () => {
    // Dugumler yerlerinde salindigi icin durgun konumda cakismamak YETMEZ:
    // iki komsu ayni anda birbirine dogru salinabilir ve aralarindaki mesafe
    // 2 x genlik kadar kapanir. Her kutuyu genlik kadar sisirmek tam olarak o
    // en kotu ani sinar.
    const layout = layoutTree(realisticOrg()) as OrgLayout;
    const boxes = labelled(layout).map(labelBox).map((box) => ({
      left: box.left - DRIFT_AMPLITUDE,
      right: box.right + DRIFT_AMPLITUDE,
      top: box.top - DRIFT_AMPLITUDE,
      bottom: box.bottom + DRIFT_AMPLITUDE,
    }));

    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        expect(
          overlaps(boxes[i], boxes[j]),
          `labels ${i} and ${j} overlap`,
        ).toBe(false);
      }
    }
  });

  it('never drops the label of someone who manages people', () => {
    // Yerlestirme sirasi TAM OLARAK bunu satin aliyor: once ust seviyeler ve
    // kalabalik ekipler yerlesir, dolayisiyla yer darsa kaybeden her zaman en
    // az onemli yaprak olur. Bir departman baskaninin etiketsiz kalmasi
    // olculmus bir kusurdu ve daraltma adimi onu kapatti.
    const layout = layoutTree(realisticOrg()) as OrgLayout;

    const unlabelledManagers = layout.nodes.filter(
      (entry) => entry.node !== null && entry.hasChildren && entry.label === null,
    );

    expect(unlabelledManagers.map((entry) => entry.node!.firstName)).toEqual([]);
  });

  it('labels the great majority of a full organisation', () => {
    // Bir avuc yaprak etiketsiz kalabilir ve bu kabul edilen bir bedeldir --
    // ama sessizce buyumemeli. Tam ad o dugumlerde de <title>'da, sagdaki
    // panelde ve gorunmeyen anahat listesinde duruyor.
    //
    // Esik salinim genligine BAGLI: pay 2 x genlik + 6 oldugu icin genligi
    // buyutmek dogrudan etiket kaybettirir. Olculen deger 36'da 31.
    const layout = layoutTree(realisticOrg()) as OrgLayout;
    const people = layout.nodes.filter((entry) => entry.node !== null);

    expect(people).toHaveLength(36);
    expect(labelled(layout).length / people.length).toBeGreaterThan(0.82);
  });

  it('keeps every label inside the canvas', () => {
    // Tasan etiket kirpilir; kirpilmis bir isim yanlis bir isimdir.
    const layout = layoutTree(realisticOrg()) as OrgLayout;

    for (const box of labelled(layout).map(labelBox)) {
      expect(box.left).toBeGreaterThanOrEqual(0);
      expect(box.right).toBeLessThanOrEqual(layout.size);
      expect(box.bottom).toBeLessThanOrEqual(layout.size);
    }
  });

  it('renders names and circles at a legible size on a real screen', () => {
    // SVG kullanici birimi MUTLAK bir olcu DEGILDIR; anlamini viewBox ile
    // kazanir. Olculer buradaki gibi ekran pikseline cevrilmeden secilirse
    // tasarim "dogru ama okunmaz" olabilir -- ve bunu hicbir cakisma testi
    // yakalamaz.
    //
    // 1920 px ekran, daraltilmis kenar cubugu (68), kabuk dolgusu (2x32),
    // departman rafi (230 + 20) ve kisi paneli (300 + 20) dusuldugunde
    // cizime ~1218 px kaliyor.
    const layout = layoutTree(realisticOrg()) as OrgLayout;
    const scale = 1218 / layout.size;

    const leaf = layout.nodes.find((entry) => entry.node !== null && !entry.hasChildren)!;

    expect(leaf.r * 2 * scale).toBeGreaterThan(18);
    expect(NAME_SIZE * scale).toBeGreaterThan(10);
    expect(TITLE_SIZE * scale).toBeGreaterThan(8);
  });
});
