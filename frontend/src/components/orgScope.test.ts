import { describe, expect, it } from 'vitest';
import {
  departmentColors, departmentInitials, departmentsOf, findNode, scopeToDepartment,
} from './orgScope';
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

describe('departmentColors', () => {
  it('gives every department a colour of its own', () => {
    // Ilk deneme ad hash'iydi ve bes departmanin ucu ayni renge dusuyordu.
    // Cakisan renk, rengin AYIRT ETME isini tamamen bitirir.
    const names = ['Software Development', 'Sales', 'Marketing', 'Accounting', 'Human Resources'];
    const colors = departmentColors(names, 'dark');

    expect(new Set([...colors.values()].map((s) => s.fill)).size).toBe(names.length);
  });

  it('falls back to a neutral rather than inventing an eleventh colour', () => {
    // Tavan BES idi ve canli veride alti departman cikinca en buyuk departman
    // renksiz kaldi; tavan ona gore ona cikarildi. Ama tavanin kendisi duruyor:
    // yalan soyleyen bir renk, renksizlikten kotudur.
    const names = Array.from({ length: 12 }, (_, i) => `Dept ${String.fromCharCode(65 + i)}`);
    const colors = departmentColors(names, 'dark');

    expect(colors.get('Dept K')?.fill).toBe(colors.get('Dept L')?.fill);
    expect(colors.get('Dept A')?.fill).not.toBe(colors.get('Dept K')?.fill);
  });

  it('does not depend on the order it was given', () => {
    const names = ['Sales', 'Accounting', 'Marketing'];
    const forwards = departmentColors(names, 'light');
    const backwards = departmentColors([...names].reverse(), 'light');

    for (const name of names) {
      expect(forwards.get(name)).toBe(backwards.get(name));
    }
  });

  it('uses a different set per theme so circles stay visible', () => {
    // Olculdu: avatar tonlari koyu temada kart yuzeyine karsi 1.64:1 veriyor,
    // yani daireler zeminden ayirt edilemiyordu.
    const light = departmentColors(['Sales'], 'light').get('Sales');
    const dark = departmentColors(['Sales'], 'dark').get('Sales');

    expect(light?.fill).not.toBe(dark?.fill);
  });
});

/** WCAG bagil parlaklik. */
function luminance(hex: string) {
  const channels = [1, 3, 5]
    .map((at) => parseInt(hex.slice(at, at + 2), 16) / 255)
    .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));

  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrast(a: string, b: string) {
  const [high, low] = [luminance(a), luminance(b)].sort((x, y) => y - x);

  return (high + 0.05) / (low + 0.05);
}

describe('department colours', () => {
  const many = (count: number) =>
    Array.from({ length: count }, (_, index) => `Dept ${String.fromCharCode(65 + index)}`);

  /** Kartin yuzeyi; daireler bunun uzerine ciziliyor. */
  const SURFACE = { light: '#FFFFFF', dark: '#1E2631' } as const;

  it.each(['light', 'dark'] as const)('stays readable on the %s surface', (mode) => {
    // Kontrast TAHMIN edilecek bir sey degil. Olcum bir kenar betiginde
    // kalsaydi paleti degistiren bir sonraki kisi onu calistirmayi hatirlamak
    // zorunda olurdu; burada duruyor ve unutulamaz.
    const colours = [...departmentColors(many(10), mode).values()];

    expect(colours).toHaveLength(10);

    for (const { fill, ink } of colours) {
      // Halka bir GRAFIK OGESI: WCAG 1.4.11 esigi 3:1.
      expect(contrast(fill, SURFACE[mode]), `${fill} on ${SURFACE[mode]}`)
        .toBeGreaterThanOrEqual(3);
      // Dolu diskin uzerindeki harf METIN: esik 4.5:1.
      expect(contrast(ink, fill), `${ink} on ${fill}`).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('gives ten departments ten distinct colours', () => {
    // Palet BES renkti ve altinci departman notr griye dusuyordu. Canli veride
    // alti departman vardi ve alfabetik sirada sonuncu olan Software
    // Development -- 49 kisinin 28'ini tasiyan EN BUYUK departman -- tam da bu
    // yuzden renksiz kaliyordu.
    const names = many(10);
    const colours = departmentColors(names, 'dark');
    const fills = names.map((name) => colours.get(name)!.fill);

    expect(new Set(fills).size).toBe(10);
    expect(fills).not.toContain('#8A94A3');
  });

  it('only falls back to the neutral past ten', () => {
    // Notr bir eksiklik degil BEYAN: on kategorik renkten sonrasi zaten ayirt
    // edilemez ve uydurma bir on birinci hue, edilebilirmis gibi gorunurdu.
    const names = many(11);
    const colours = departmentColors(names, 'dark');

    expect(colours.get('Dept K')!.fill).toBe('#8A94A3');
  });
});

describe('departmentInitials', () => {
  it('takes one letter from each word', () => {
    expect(departmentInitials('Software Development')).toBe('SD');
    expect(departmentInitials('Human Resources')).toBe('HR');
  });

  it('takes two letters when the name is a single word', () => {
    // Tek harf yetmezdi: "Sales" ve "Software Development" ayni "S" ile
    // gorunur ve merkezdeki isaret hangi departmanda oldugunu soylemezdi.
    expect(departmentInitials('Sales')).toBe('SA');
    expect(departmentInitials('Accounting')).toBe('AC');
    expect(departmentInitials('Marketing')).toBe('MA');
  });

  it('does not grow past three letters', () => {
    // Isaret dairenin ICINE siğmali; uzun bir kisaltma tasar.
    expect(departmentInitials('Research Development And Innovation')).toBe('RDA');
  });
});
