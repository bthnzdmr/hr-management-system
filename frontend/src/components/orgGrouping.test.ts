import { describe, expect, it } from 'vitest';
import { groupByDepartment, isDepartmentNode } from './orgScope';
import type { OrgNode } from '../api/orgChart';

function person(id: number, department: string, reports: OrgNode[] = []): OrgNode {
  return {
    id, firstName: `P${id}`, lastName: 'X', jobTitle: 'Engineer',
    departmentName: department, depth: 0, reports,
  };
}

describe('groupByDepartment', () => {
  it('puts a department bubble between the centre and its people', () => {
    const roots = [person(1, 'Sales', [person(2, 'Sales')])];

    const [department] = groupByDepartment(roots);

    expect(isDepartmentNode(department)).toBe(true);
    expect(department.firstName).toBe('Sales');
    expect(department.reports.map((r) => r.id)).toEqual([1]);
  });

  it('keeps the reporting chain inside a department', () => {
    // Departman katmani gruplama icindir; ekipleri DUZLESTIRMEZ.
    const roots = [person(1, 'Sales', [person(2, 'Sales', [person(3, 'Sales')])])];

    const [sales] = groupByDepartment(roots);

    expect(sales.reports[0].reports[0].reports.map((r) => r.id)).toEqual([3]);
  });

  it('gives a person whose manager sits elsewhere their own department bubble', () => {
    const roots = [person(1, 'Sales', [person(2, 'Finance')])];

    const names = groupByDepartment(roots).map((d) => d.firstName).sort();

    expect(names).toEqual(['Finance', 'Sales']);
  });

  it('never collides with a real employee id', () => {
    // Gercek id'ler pozitif; departman balonlari negatif. Cakissalardi
    // bir departmana tiklamak bir KISIYI secerdi.
    const ids = groupByDepartment([person(1, 'Sales'), person(2, 'Finance')])
      .map((d) => d.id);

    expect(ids.every((id) => id < 0)).toBe(true);
    expect(new Set(ids).size).toBe(ids.length);
  });
});
