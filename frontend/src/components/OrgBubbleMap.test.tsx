import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { OrgOutline } from './OrgBubbleMap';
import type { OrgNode } from '../api/orgChart';

function node(id: number, reports: OrgNode[] = []): OrgNode {
  return {
    id,
    firstName: 'Test',
    lastName: `P${id}`,
    jobTitle: 'Engineer',
    departmentName: 'Sales',
    depth: 1,
    reports,
  };
}

describe('OrgOutline', () => {
  it('is visible so a browser find can actually show the match', () => {
    // Liste uzun sure 1px'e kirpiliydi ve yalnizca ekran okuyucu icindi.
    // Olculdu: Ctrl+F eslesmeyi BULUYOR ama kirpilmis bir kutuya kaydirdigi
    // icin ekranda hicbir sey gorunmuyor -- sayac "1/34" derken kullanici
    // bos ekrana bakiyor. Bulunan ama gosterilemeyen bir eslesme, hic
    // bulunmamaktan daha kafa karistiricidir.
    //
    // Iddia listenin KENDISINDEN yukari yuruyor: ilk yazdigim hali yalnizca
    // en dis kutuya bakiyordu ve ic kutuyu gizleyerek curutmeye calistigimda
    // KIRILMADI. Gizleme zincirin herhangi bir halkasinda olabilir.
    const { container, getByRole } = render(<OrgOutline nodes={[node(1, [node(2)])]} />);

    let element: HTMLElement | null = getByRole('list', { name: 'Reporting structure' });

    while (element && element !== container) {
      const style = getComputedStyle(element);

      expect(style.clipPath, `clipPath on <${element.tagName.toLowerCase()}>`)
        .not.toBe('inset(50%)');
      expect(style.width, `width on <${element.tagName.toLowerCase()}>`).not.toBe('1px');
      expect(style.display, `display on <${element.tagName.toLowerCase()}>`).not.toBe('none');

      element = element.parentElement;
    }
  });

  it('names the list with the heading the reader can see', () => {
    // Gorunur bir baslik varken ayrica `aria-label` yazmak ayni metni IKI KEZ
    // okuturdu.
    const { getByRole } = render(<OrgOutline nodes={[node(1)]} />);

    expect(getByRole('list', { name: 'Reporting structure' })).toBeInTheDocument();
  });

  it('carries every name as real text, however deep in the tree', () => {
    // Ctrl+F ve ekran okuyucu garantisi buna dayaniyor: cizimdeki isim SVG
    // metnidir ve etiket sigmayan dugumlerde HIC cizilmez -- o kisiler
    // yalnizca burada bulunur.
    const { getByText } = render(<OrgOutline nodes={[node(1, [node(2)])]} />);

    expect(getByText(/Test P2/)).toBeInTheDocument();
  });
});
