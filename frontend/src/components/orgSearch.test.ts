import { describe, expect, it } from 'vitest';
import { MIN_QUERY, everyone, search, splitByScope } from './orgSearch';
import type { OrgNode } from '../api/orgChart';

function person(
  id: number,
  firstName: string,
  lastName: string,
  department = 'Sales',
  jobTitle = 'Engineer',
  reports: OrgNode[] = [],
): OrgNode {
  return { id, firstName, lastName, jobTitle, departmentName: department, depth: 1, reports };
}

/** Departman dugumleri sentetiktir ve NEGATIF id tasir. */
function departmentNode(id: number, name: string, reports: OrgNode[]): OrgNode {
  return { id, firstName: name, lastName: '', jobTitle: '', departmentName: name, depth: 0, reports };
}

const tree: OrgNode[] = [
  departmentNode(-1, 'Sales', [
    person(1, 'Grace', 'Hopper', 'Sales', 'Compiler Engineer', [
      person(2, 'Ada', 'Lovelace', 'Sales'),
    ]),
  ]),
  departmentNode(-2, 'Accounting', [
    person(3, 'Mary', 'Addison', 'Accounting', 'Accountant'),
  ]),
];

describe('everyone', () => {
  it('flattens the whole tree, however deep', () => {
    expect(everyone(tree).map((n) => n.id)).toEqual([1, 2, 3]);
  });

  it('leaves out the synthetic department nodes', () => {
    // Onlar bir KISI degil. Arama sonucunda cikarsalardi "Sales" yazan
    // kullanici bir departmani secmis olurdu -- oysa departman zaten rafta.
    expect(everyone(tree).some((n) => n.id < 0)).toBe(false);
  });
});

describe('search', () => {
  it('finds somebody by name regardless of case', () => {
    expect(search(tree, 'hopper').map((m) => m.node.id)).toEqual([1]);
    expect(search(tree, 'HOPPER').map((m) => m.node.id)).toEqual([1]);
  });

  it('finds somebody by job title too', () => {
    // "Kim muhasebeci" da mesru bir soru ve unvan zaten dugumde yaziyor.
    expect(search(tree, 'accountant').map((m) => m.node.id)).toEqual([3]);
  });

  it('crosses department boundaries', () => {
    // ARAMANIN VARLIK SEBEBI. Yalnizca cizili kapsama bakan bir arama, "bu
    // kisi nerede" sorusunu -- yani sorulan tek soruyu -- cevaplayamaz.
    expect(search(tree, 'addison')).toHaveLength(1);
  });

  it('reports which department each match sits in', () => {
    // Kapsam disi eslesmede ekranda "Mary Addison · Accounting" yaziyor;
    // departman olmadan kullanici nereye gidecegini bilemez.
    expect(search(tree, 'addison')[0].department).toBe('Accounting');
  });

  it('ignores a query too short to narrow anything', () => {
    // Tek harf butun kadroyu eslestirir ve 34 vurgulu dugum, hicbirinin
    // vurgulu olmamasiyla ayni sey.
    expect(search(tree, 'a')).toHaveLength(0);
    expect(MIN_QUERY).toBe(2);
  });

  it('ignores whitespace typed on its own', () => {
    expect(search(tree, '   ')).toHaveLength(0);
  });

  it('returns nothing rather than everything when nobody matches', () => {
    expect(search(tree, 'zzzz')).toHaveLength(0);
  });
});

describe('splitByScope', () => {
  it('separates what is drawn from what is somewhere else', () => {
    // Cizili olanlar semada vurgulanir; digerleri ancak TIKLANIRSA oraya
    // gecirir -- sema kendiliginden yeniden duzenlenmez.
    // "ac" ucunu de yakalar: Grace, Lovelace ve Mary'nin unvani Accountant.
    // Tek harflik bir terim MIN_QUERY'ye takilip bos donerdi -- kurgu,
    // olcmek istedigi durumu gercekten uretmeli.
    const found = search(tree, 'ac');
    const { inScope, elsewhere } = splitByScope(found, new Set([1, 2]));

    expect(inScope.map((m) => m.node.id)).toEqual([1, 2]);
    expect(elsewhere.map((m) => m.node.id)).toEqual([3]);
  });

  it('puts everything in scope when the whole chart is drawn', () => {
    const found = search(tree, 'ac');

    expect(found).toHaveLength(3);
    expect(splitByScope(found, new Set([1, 2, 3])).elsewhere).toHaveLength(0);
  });
});
