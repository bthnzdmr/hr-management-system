import { describe, expect, it } from 'vitest';
import { departmentsOf, findNode, scopeToDepartment } from './orgScope';
import type { OrgNode } from '../api/orgChart';

function node(id: number, department: string, reports: OrgNode[] = []): OrgNode {
  return {
    id,
    firstName: 'Test',
    lastName: `P${id}`,
    jobTitle: 'Engineer',
    departmentName: department,
    depth: 1,
    reports,
  };
}

/**
 * CTO (Engineering)
 *  ├── VP Eng (Engineering)
 *  │    ├── Dev (Engineering)
 *  │    └── Designer (Design)
 *  └── Head of Sales (Sales)
 *       └── Rep (Sales)
 */
const organisation = [
  node(1, 'Engineering', [
    node(2, 'Engineering', [
      node(3, 'Engineering'),
      node(4, 'Design'),
    ]),
    node(5, 'Sales', [node(6, 'Sales')]),
  ]),
];

describe('orgScope', () => {
  it('counts everyone in a department, however deep they sit', () => {
    expect(departmentsOf(organisation)).toEqual([
      { name: 'Engineering', headcount: 3 },
      { name: 'Sales', headcount: 2 },
      { name: 'Design', headcount: 1 },
    ]);
  });

  it('finds the head of a department by where the chain leaves it', () => {
    // Sabit bir "baskan" alani saklamiyoruz: bilgi zaten raporlama
    // cizgisinde duruyor. Satis baskani CTO'ya bagli, yani zincir yukari
    // dogru departmandan cikiyor -- orasi tepedir.
    const sales = scopeToDepartment(organisation, 'Sales');

    expect(sales).toHaveLength(1);
    expect(sales[0].id).toBe(5);
    expect(sales[0].reports.map((r) => r.id)).toEqual([6]);
  });

  it('leaves out people who belong to another department', () => {
    // Tasarimci muhendislik yoneticisine bagli ama muhendis degil; muhendislik
    // kapsaminda gorunmesi departman sayilarini yalanci yapardi.
    const engineering = scopeToDepartment(organisation, 'Engineering');

    expect(engineering).toHaveLength(1);
    expect(engineering[0].reports[0].reports.map((r) => r.id)).toEqual([3]);
  });

  it('returns every head when a department has more than one', () => {
    // Ayni departmanda birbirine baglanmayan iki ekip olabilir; bu bir veri
    // hatasi degildir, o yuzden biri secilip digeri atilmaz.
    const split = [
      node(1, 'Engineering', [node(2, 'Design'), node(3, 'Design')]),
    ];

    expect(scopeToDepartment(split, 'Design').map((n) => n.id)).toEqual([2, 3]);
  });

  it('does not modify the tree it was given', () => {
    // Budama kopya uretir: kaynak agac degistirilse baska bir departmani
    // secmek eksik veri gosterirdi.
    scopeToDepartment(organisation, 'Engineering');

    expect(organisation[0].reports[0].reports).toHaveLength(2);
  });

  it('finds a person anywhere in the tree', () => {
    expect(findNode(organisation, 6)?.id).toBe(6);
    expect(findNode(organisation, 99)).toBeNull();
  });
});
