import { describe, expect, it } from 'vitest';
import { describeDetail } from './auditDetail';

/**
 * Girdiler UYDURULMADI: `AuditAspect` metot argumanlarini "ad=deger" diye
 * yaziyor ve asagidakiler o uretimin gercek ciktilari.
 */
describe('describeDetail', () => {
  it('turns a status flag into a verb', () => {
    // Ekranda gorunen sikayet buydu: "active = true".
    expect(describeDetail('active=true')).toBe('Activated');
    expect(describeDetail('active=false')).toBe('Deactivated');
  });

  it('adds the termination reason next to the status', () => {
    expect(describeDetail('active=false, reason=RESIGNED'))
      .toBe('Deactivated · Resigned');
  });

  it('does not split a list on its inner comma', () => {
    // Duz bir split(', ') "roles=[EMPLOYEE, MANAGER]" ifadesini ikiye keser ve
    // "MANAGER]" diye bir alan uretirdi.
    expect(describeDetail('roles=[EMPLOYEE, MANAGER]'))
      .toBe('Roles: Employee, Manager');
  });

  it('unwraps a request record and drops the wrapper name', () => {
    // "UserCreateRequest" ekranda hicbir sey anlatmiyor; icindeki alanlar
    // anlatiyor.
    expect(describeDetail('request=UserCreateRequest[email=ada@example.com, roles=[EMPLOYEE]]'))
      .toBe('Email: ada@example.com · Roles: Employee');
  });

  it('never shows a masked secret', () => {
    // Parola DTO'nun toString'inde zaten maskeleniyor; maskeyi ekrana tasimanin
    // faydasi yok.
    expect(describeDetail('request=UserCreateRequest[email=ada@example.com, password=***]'))
      .toBe('Email: ada@example.com');
  });

  it('formats dates the same way as the rest of the app', () => {
    expect(describeDetail('request=LeaveRequestCreateRequest[startDate=2031-03-10, endDate=2031-03-15]'))
      .toBe('From: 10-03-2031 · To: 15-03-2031');
  });

  it('capitalises a bare summary word', () => {
    // Izin kararlari `@Auditable(summary = "approved")` kullaniyor.
    expect(describeDetail('approved')).toBe('Approved');
    expect(describeDetail('withdrawn')).toBe('Withdrawn');
  });

  it('keeps anything it does not recognise', () => {
    // Bilgi KAYBETMEK, okunmasi zor bir satirdan kotudur.
    expect(describeDetail('somethingNew=42')).toBe('somethingNew: 42');
  });

  it('reports nothing for an empty detail', () => {
    expect(describeDetail(null)).toBeNull();
    expect(describeDetail('   ')).toBeNull();
  });

  it('leaves a sentence written by the server untouched', () => {
    // Sunucu artik okunur cumleler yaziyor. Ayristirmak yalnizca zarar
    // verirdi: splitFields en ust seviyedeki virgullerden boler ve rol
    // listesini ikiye ayirirdi.
    expect(describeDetail('Active · Roles: HR specialist, Manager · Linked to Ada Lovelace'))
      .toBe('Active · Roles: HR specialist, Manager · Linked to Ada Lovelace');
  });

  it('still unpacks an old row written in the machine format', () => {
    // Sunucuda bicimi degistirmek GECMISI duzeltmez; o satirlar sonsuza
    // kadar ham kalirdi, bu yuzden ayristirici duruyor.
    expect(describeDetail('active=false, reason=RESIGNED')).toBe('Deactivated · Resigned');
  });
});
