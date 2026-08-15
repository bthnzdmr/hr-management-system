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
  it('takes up a single pixel, not the whole page', () => {
    // MUI'nin olcu kisayolunda 1'den kucuk esit sayilar YUZDEDIR: `width: 1`
    // bir piksel degil %100 demektir. Anahat bu yuzden tam ekran boyutunda
    // duruyor ve `nowrap` icerigiyle sayfayi yatayda tasiriyordu.
    const { container } = render(<OrgOutline nodes={[node(1, [node(2)])]} />);

    const box = container.firstElementChild as HTMLElement;
    const style = getComputedStyle(box);

    expect(style.width).toBe('1px');
    expect(style.height).toBe('1px');
  });

  it('still carries every name as real text', () => {
    // Gorunmez olmak, DOM'dan silinmek degildir: Ctrl+F ve ekran okuyucu
    // garantisi buna dayaniyor.
    const { getByText } = render(<OrgOutline nodes={[node(1, [node(2)])]} />);

    expect(getByText(/Test P2/)).toBeInTheDocument();
  });
});
