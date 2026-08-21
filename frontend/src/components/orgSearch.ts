import type { OrgNode } from '../api/orgChart';
import { fullName, isDepartmentNode } from './orgScope';

/**
 * Haritada kisi arama -- saf mantik.
 *
 * Bilesenden AYRI bir dosyada: burada sinanan sey "kim eslesir" sorusu ve o
 * soru bir React agaci kurmadan cevaplanabilir. Ayni ayrim `orgTree` /
 * `OrgBubbleMap` ve `leaveTimeline` / `LeaveCalendar` ikililerinde de var.
 *
 * Dosya adi bilerek `orgSearch`, bilesen ise `OrgSearchField`: iki modul adi
 * YALNIZCA harf buyuklugu ile ayrilmaz. Windows ve macOS dosya sistemleri
 * harf duyarsizdir ve import sessizce yanlis dosyaya cozulur -- bu projede
 * bir kez yasandi ve tip kontrolu bile temiz gecmisti.
 */
export interface Match {
  node: OrgNode;
  /** Kisinin bulundugu departman; kapsam disi eslesmelerde ekranda yazar. */
  department: string;
}

/** Bir arama teriminin anlamli sayilmasi icin gereken en az uzunluk. */
export const MIN_QUERY = 2;

/**
 * Butun agactaki kisiler, duzlestirilmis.
 *
 * Departman dugumleri ELENIR: onlar sentetik ve bir kisi degiller. Aranan sey
 * "kim nerede", "hangi departmanlar var" degil -- departman zaten rafta.
 */
export function everyone(roots: OrgNode[]): OrgNode[] {
  const flat: OrgNode[] = [];

  const walk = (nodes: OrgNode[]) => {
    for (const node of nodes) {
      if (!isDepartmentNode(node)) flat.push(node);
      walk(node.reports);
    }
  };

  walk(roots);

  return flat;
}

/**
 * Terimi ada ve unvana uygular.
 *
 * `toLowerCase()` YEREL AYARDAN BAGIMSIZDIR -- Java'nin aynı adli metodundan
 * farki budur ve orada Turkce bir makinede `I` harfi bozulup uc test
 * dusurmustu. JavaScript'te yerele duyarli olan `toLocaleLowerCase()`;
 * burada onu kullanmak aramayi makineye gore degistirirdi.
 */
export function search(roots: OrgNode[], query: string): Match[] {
  const term = query.trim().toLowerCase();

  // Tek harf butun kadroyu eslestirir ve "arama" hicbir sey daraltmaz;
  // ekranda 34 vurgulu dugum, hicbiri vurgulu olmamasiyla ayni sey.
  if (term.length < MIN_QUERY) return [];

  return everyone(roots)
    .filter((node) => fullName(node).toLowerCase().includes(term)
      || node.jobTitle.toLowerCase().includes(term))
    .map((node) => ({ node, department: node.departmentName }));
}

/**
 * Eslesmeleri "cizili olanlar" ve "baska yerde olanlar" diye ayirir.
 *
 * Cizili olanlar semada vurgulanir; digerleri bir liste olarak sunulur ve
 * ancak TIKLANIRSA o departmana gecilir. Sema kendiliginden degismez --
 * "birincil etkilesim gorseli yeniden duzenlememelidir" kuralinin arama
 * tarafindaki karsiligi.
 */
export function splitByScope(matches: Match[], visibleIds: Set<number>) {
  const inScope: Match[] = [];
  const elsewhere: Match[] = [];

  for (const match of matches) {
    (visibleIds.has(match.node.id) ? inScope : elsewhere).push(match);
  }

  return { inScope, elsewhere };
}
