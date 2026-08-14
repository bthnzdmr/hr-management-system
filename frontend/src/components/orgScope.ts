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
 * <p>Yeni bir renk ailesi UYDURULMADI: tonlar `InitialsAvatar`'daki avatar
 * zeminlerinin ta kendisi, yani paletin hue ailesinden.
 *
 * <p><b>Neden iki set?</b> Olculdu: avatar tonlari koyu temada kart yuzeyine
 * (#1E2631) karsi yalnizca <b>1,64:1</b> veriyor, yani daireler zeminden
 * ayirt edilemiyordu. Koyu tema icin ayni hue'lar beyaza dogru %30 karistirilip
 * acildi; her iki set de olculdu ve gecti:
 *
 * <ul>
 *   <li>daire / kart yuzeyi: en dusuk <b>3,97:1</b> (grafik esigi 3:1)</li>
 *   <li>bas harfler / daire: en dusuk <b>4,55:1</b> (metin esigi 4,5:1)</li>
 * </ul>
 *
 * <p>Metin rengi de sete gore ters cevrilir: koyu zeminde acik, acik zeminde
 * koyu. Kontrast tahmin edilecek bir sey degil, olculecek bir seydir.
 */
const DEPARTMENT_COLORS = {
  light: ['#3B4859', '#43615C', '#8A5238', '#7A4B4B', '#2F5364', '#5A5468'],
  dark: ['#7A838E', '#7B908D', '#AD8674', '#A28181', '#6D8793', '#8C8795'],
} as const;

/** Renklerin uzerine yazilan metin; sete gore ters cevrilir. */
export const DEPARTMENT_INK = { light: '#F8FAFC', dark: '#141A22' } as const;

/**
 * Departman -> renk eslemesi.
 *
 * <p><b>Ilk deneme ad HASH'iydi ve olculunce cop cikti:</b> bes departmanin
 * ucu ayni renge dusuyordu. Cakisan renk, rengin AYIRT ETME isini tamamen
 * bitirir -- iki departman ayni gorunuyorsa renk bilgi tasimiyor demektir.
 *
 * <p>Simdi renk, ALFABETIK siradaki indekse gore veriliyor: alti departmana
 * kadar cakisma yapisal olarak imkansiz. Bedeli, araya yeni bir departman
 * girdiginde ondan SONRAKILERIN renginin kaymasi; hash'te bu olmazdi ama
 * cakisma olurdu ve ikisi arasinda ayirt edilebilirlik daha degerlidir.
 */
export function departmentColors(names: string[], mode: 'light' | 'dark') {
  const set = DEPARTMENT_COLORS[mode];

  return new Map(
    [...names].sort((a, b) => a.localeCompare(b))
      .map((name, index) => [name, set[index % set.length]] as const),
  );
}
