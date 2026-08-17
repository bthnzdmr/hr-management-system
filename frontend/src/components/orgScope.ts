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

/** Bir departmanin kendi agaci. */
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

/** Departman renkleri. */
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

/** Departman -> renk eslemesi. */
export function departmentColors(names: string[], mode: 'light' | 'dark') {
  const set = DEPARTMENT_SWATCHES[mode];

  return new Map(
    [...names].sort((a, b) => a.localeCompare(b))
      .map((name, index) => [name, set[index] ?? NEUTRAL[mode]] as const),
  );
}

/**
 * Kisileri departman balonlarinin altina toplar.
 *
 * Sema onceden butun kisileri TEK merkezden dagitiyordu; departman yalnizca
 * renkten okunuyordu. Araya bir katman girince "kim hangi departmanda"
 * konumdan okunuyor -- renk bir kodlama, konum ise yapinin kendisi.
 *
 * Raporlama zinciri KORUNUR: departman dugumu yalnizca o departmanin
 * BASLARINI toplar, altlarindaki ekipler oldugu gibi dallanmaya devam eder.
 * Baskan, departmanda olup yoneticisi ayni departmanda OLMAYAN kisidir --
 * departman rafindaki hesabin aynisi.
 */
/**
 * Departman balonlarinin id araligi.
 *
 * -1 SERBEST DEGIL: yerlesim kendi gorunmez merkezini o id ile isaretliyor
 * (orgTree'deki `isHub`). Departmanlar -1'den baslayinca ilki merkez sanildi
 * ve React "iki cocuk ayni anahtari tasiyor" dedi. Ayri bir aralik, iki
 * sozlesmenin ayni sentinel'i paylasmasini engelliyor.
 */
const DEPARTMENT_ID_BASE = -1000;

export function groupByDepartment(roots: OrgNode[]): OrgNode[] {
  return departmentsOf(roots).map((department, index) => ({
    // Negatif id: gercek personel id'leriyle CAKISMAZ ve arayuz bir dugumun
    // kisi mi departman mi oldugunu buradan anlar.
    id: DEPARTMENT_ID_BASE - index,
    firstName: department.name,
    lastName: '',
    jobTitle: `${department.headcount} people`,
    departmentName: department.name,
    depth: 0,
    // Alt agacin TAMAMI bir halka disari kayar. Kaydirilmasaydi departman
    // balonu ile onun baskani AYNI halkaya duserdi ve semanin en temel
    // degismezi -- her seviye kendi halkasinda -- bozulurdu.
    reports: scopeToDepartment(roots, department.name).map((head) => shift(head, 1)),
  }));
}

/** Dugumun ve butun altinin derinligini kaydirir. */
function shift(node: OrgNode, by: number): OrgNode {
  return {
    ...node,
    depth: node.depth + by,
    reports: node.reports.map((report) => shift(report, by)),
  };
}

/** Bir dugum gercek bir kisi mi, yoksa departman balonu mu? */
export function isDepartmentNode(node: { id: number }): boolean {
  return node.id < 0;
}
