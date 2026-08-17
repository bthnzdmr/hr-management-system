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
  // Departman dugumlerinin soyadi yok; kirpilmasaydi adin sonunda bir bosluk
  // kalir ve etiket genisligi hesabi bir harf sisirilirdi.
  return `${node.firstName} ${node.lastName}`.trim();
}

export function initials(node: OrgNode) {
  return `${node.firstName.charAt(0)}${node.lastName.charAt(0)}`.toUpperCase();
}

/**
 * Bir departmanin merkezde gosterilecek isareti.
 *
 * Tek kelimelik adlarda ilk iki harf alinir: "Sales" icin yalnizca "S" yazmak
 * "Software Development"tan ayirt edilemezdi.
 */
export function departmentInitials(name: string) {
  const words = name.trim().split(/\s+/).filter(Boolean);

  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();

  return words.slice(0, 3).map((word) => word.charAt(0)).join('').toUpperCase();
}

/** Departman renkleri. */
export interface Swatch {
  fill: string;
  /** Uzerine yazilan metin; renge gore secilir, temaya gore degil. */
  ink: string;
}

/**
 * Departman renkleri: on hue, on departmana kadar.
 *
 * Once BES renk vardi ve altinci departman notr griye dusuyordu. Olculdu:
 * canli veride alti departman var ve alfabetik sirada sonuncu olan
 * "Software Development" -- 49 kisinin 28'ini tasiyan en buyuk departman --
 * tesadufen renksiz kaliyordu.
 *
 * SIRA cember sirasi DEGIL. Renkler departmanlara alfabetik indise gore
 * veriliyor, yani komsu indisler ekranda yan yana gelir; cember sirasiyla
 * dizilseydi ilk iki departman kirmizi ve turuncu olurdu. Bu dizilimde ardisik
 * iki renk arasinda en az 98 derece var.
 *
 * Degerler elle secilmedi, URETILDI ve olculdu (bkz. asagidaki oranlar):
 * halka zemine karsi >= 3:1 (WCAG 1.4.11, grafik ogeleri) ve dolu diskin
 * uzerindeki harf >= 4.5:1. Acik temada ilk deneme harfte 4.09 verdi ve
 * dolgular bir kademe koyulastirildi.
 *
 * BILINEN SINIR: on kategorik renk, ayirt edilebilirligin ust sinirindadir ve
 * bu set renk korlugune dayanikli DEGILDIR -- onceki bes renklik set
 * Okabe-Ito'ydu, on hue'ya cikarken o ozellik korunamaz. Departman rafindaki
 * ad ve semadaki etiket, rengi tek tasiyici olmaktan cikariyor.
 */
const DEPARTMENT_SWATCHES: Record<'light' | 'dark', Swatch[]> = {
  // Zemin #FFFFFF: en kotu halka 5.40:1, en kotu harf 5.16:1
  light: [
    { fill: '#2E6CAB', ink: '#F8FAFC' },
    { fill: '#915F27', ink: '#F8FAFC' },
    { fill: '#20794A', ink: '#F8FAFC' },
    { fill: '#B93187', ink: '#F8FAFC' },
    { fill: '#6D6D1D', ink: '#F8FAFC' },
    { fill: '#553ECC', ink: '#F8FAFC' },
    { fill: '#377720', ink: '#F8FAFC' },
    { fill: '#BD3C32', ink: '#F8FAFC' },
    { fill: '#207477', ink: '#F8FAFC' },
    { fill: '#A533C1', ink: '#F8FAFC' },
  ],
  // Zemin #1E2631: en kotu halka 6.01:1, en kotu harf 6.89:1
  dark: [
    { fill: '#70A7DE', ink: '#141A22' },
    { fill: '#D5964D', ink: '#141A22' },
    { fill: '#2CBA6E', ink: '#141A22' },
    { fill: '#E285C0', ink: '#141A22' },
    { fill: '#BABA2C', ink: '#141A22' },
    { fill: '#A79AE7', ink: '#141A22' },
    { fill: '#52BA2C', ink: '#141A22' },
    { fill: '#E28B85', ink: '#141A22' },
    { fill: '#2CB5BA', ink: '#141A22' },
    { fill: '#D187E3', ink: '#141A22' },
  ],
};

/**
 * On birinci departmandan itibaren kullanilan notr.
 *
 * Bu bir eksiklik degil bir BEYAN: on kategorik renkten sonrasi zaten ayirt
 * edilemez ve uydurma bir on birinci hue, ayirt edilebilirmis gibi gorunerek
 * yaniltirdi.
 */
const NEUTRAL: Record<'light' | 'dark', Swatch> = {
  light: { fill: '#5A6472', ink: '#F8FAFC' },
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
export function groupByDepartment(roots: OrgNode[]): OrgNode[] {
  return departmentsOf(roots).map((department, index) => ({
    // Negatif id: gercek personel id'leriyle CAKISMAZ ve arayuz bir dugumun
    // kisi mi departman mi oldugunu buradan anlar.
    id: -(index + 1),
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
