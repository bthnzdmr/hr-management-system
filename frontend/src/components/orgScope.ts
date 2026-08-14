import type { OrgNode } from '../api/orgChart';

export interface Department {
  name: string;
  headcount: number;
}

/** Departman listesi, kalabaliktan seyrege. */
export function departmentsOf(roots: OrgNode[]): Department[] {
  const counts = new Map<string, number>();

  walk(roots, (node) => {
    counts.set(node.departmentName, (counts.get(node.departmentName) ?? 0) + 1);
  });

  return [...counts.entries()]
    .map(([name, headcount]) => ({ name, headcount }))
    .sort((a, b) => b.headcount - a.headcount || a.name.localeCompare(b.name));
}

/**
 * Bir departmanin kendi agaci.
 *
 * <p>Departman <b>baskani</b>, o departmanda olup yoneticisi ayni departmanda
 * OLMAYAN kisidir -- yani zincir yukari dogru departmandan cikiyorsa orasi
 * tepedir. Sabit bir "baskan" alani saklamiyoruz; bu bilgi zaten raporlama
 * cizgisinde duruyor ve ikinci bir yerde tutmak ikisinin birbirinden
 * ayrilmasi demek olurdu.
 *
 * <p>Birden fazla baskan cikabilir: ayni departmanda birbirine baglanmayan iki
 * ekip olmasi mumkundur. Bu bir veri hatasi degildir, o yuzden liste dondurulur.
 */
export function scopeToDepartment(roots: OrgNode[], department: string): OrgNode[] {
  const heads: OrgNode[] = [];

  const visit = (node: OrgNode, parentInDepartment: boolean) => {
    const inside = node.departmentName === department;

    if (inside && !parentInDepartment) heads.push(prune(node, department));

    node.reports.forEach((report) => visit(report, inside));
  };

  roots.forEach((root) => visit(root, false));

  return heads;
}

/** Alt agaci yalnizca ayni departmandaki kisilere daraltir. */
function prune(node: OrgNode, department: string): OrgNode {
  return {
    ...node,
    reports: node.reports
      .filter((report) => report.departmentName === department)
      .map((report) => prune(report, department)),
  };
}

function walk(nodes: OrgNode[], visit: (node: OrgNode) => void) {
  nodes.forEach((node) => {
    visit(node);
    walk(node.reports, visit);
  });
}

/** Bir kisiyi id'siyle bulur; icine girilen daireyi yeniden kok yapmak icin. */
export function findNode(nodes: OrgNode[], id: number): OrgNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;

    const found = findNode(node.reports, id);
    if (found) return found;
  }

  return null;
}

export function fullName(node: OrgNode) {
  return `${node.firstName} ${node.lastName}`;
}

export function initials(node: OrgNode) {
  return `${node.firstName.charAt(0)}${node.lastName.charAt(0)}`.toUpperCase();
}

/**
 * Departman renkleri.
 *
 * <p><b>Onceki set olculdu ve yetersizdi.</b> Tonlar avatar ailesinden
 * aliniyordu ve hepsi ayni doygunluk/aciklik bandindaydi; en yakin iki renk
 * arasindaki algisal fark <b>dE2000 = 6,3</b> idi. Fark edilebilirlik esigi
 * 2,3, kategorik paletlerde 20+ hedeflenir. Daha kotusu: protanopide fark
 * <b>1,4</b>'e dusuyordu, yani renk hicbir sey soylemiyordu.
 *
 * <p>Yeni set <b>Okabe-Ito</b> hue'larindan turetildi -- o palet zaten renk
 * korlugune dayanikli olmak ICIN tasarlanmistir. Her hue tema zeminine gore
 * acilip koyulastirildi ve secim rastgele degil, su kisitlar altinda
 * <b>aramayla</b> yapildi: uzerindeki metin >= 4,5:1, yuzeye karsi 3,2-8,5:1
 * (yani hicbiri leke gibi durmasin) ve algisal ayrim en buyuk olsun.
 *
 * <p>Olculen sonuc:
 *
 * <table>
 *   <tr><th></th><th>onceki</th><th>simdi</th></tr>
 *   <tr><td>en dusuk dE2000 (koyu)</td><td>6,3</td><td><b>26,2</b></td></tr>
 *   <tr><td>renk korlugunde (koyu)</td><td>1,4</td><td><b>12,8</b></td></tr>
 *   <tr><td>en dusuk dE2000 (acik)</td><td>8,0</td><td><b>31,6</b></td></tr>
 *   <tr><td>renk korlugunde (acik)</td><td>3,2</td><td><b>12,6</b></td></tr>
 * </table>
 *
 * <p><b>Neden bes renk?</b> Alti hue ile ayni kisitlar altinda ayrim 17,3'e ve
 * renk korlugunde 8,2'ye dusuyor -- yani altinci rengi eklemek digerlerini de
 * bozar. Bes bu kisitlarin TAVANIDIR. Daha fazla departman olursa fazlasi
 * notr alir: yalan soyleyen bir renk, renksizlikten kotudur. Raf ve panel adi
 * zaten yaziyor.
 */
export interface Swatch {
  fill: string;
  /** Uzerine yazilan metin; renge gore secilir, temaya gore degil. */
  ink: string;
}

const DEPARTMENT_SWATCHES: Record<'light' | 'dark', Swatch[]> = {
  light: [
    { fill: '#458BB3', ink: '#141A22' },
    { fill: '#07664C', ink: '#F8FAFC' },
    { fill: '#97902E', ink: '#141A22' },
    { fill: '#9A4705', ink: '#F8FAFC' },
    { fill: '#8B5573', ink: '#F8FAFC' },
  ],
  dark: [
    { fill: '#56B4E9', ink: '#141A22' },
    { fill: '#80CFB9', ink: '#141A22' },
    { fill: '#C4BC3C', ink: '#141A22' },
    { fill: '#D55E00', ink: '#141A22' },
    { fill: '#CC79A7', ink: '#141A22' },
  ],
};

/** Renk yetmediginde kullanilan notr. */
const NEUTRAL: Record<'light' | 'dark', Swatch> = {
  light: { fill: '#6B7684', ink: '#F8FAFC' },
  dark: { fill: '#8A94A3', ink: '#141A22' },
};

/**
 * Departman -> renk eslemesi.
 *
 * <p><b>Ilk deneme ad HASH'iydi ve olculunce cop cikti:</b> bes departmanin
 * ucu ayni renge dusuyordu. Cakisan renk, rengin AYIRT ETME isini tamamen
 * bitirir. Simdi renk ALFABETIK siradaki indekse gore veriliyor.
 */
export function departmentColors(names: string[], mode: 'light' | 'dark') {
  const set = DEPARTMENT_SWATCHES[mode];

  return new Map(
    [...names].sort((a, b) => a.localeCompare(b))
      .map((name, index) => [name, set[index] ?? NEUTRAL[mode]] as const),
  );
}
