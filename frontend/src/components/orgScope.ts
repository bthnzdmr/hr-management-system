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
