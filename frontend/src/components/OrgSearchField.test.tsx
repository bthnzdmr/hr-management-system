import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrgSearchField } from './OrgSearchField';
import type { Match } from './orgSearch';
import type { OrgNode } from '../api/orgChart';

function match(id: number, first: string, last: string, department: string): Match {
  const node: OrgNode = {
    id, firstName: first, lastName: last, jobTitle: 'Engineer',
    departmentName: department, depth: 1, reports: [],
  };

  return { node, department };
}

function renderField(overrides: Partial<Parameters<typeof OrgSearchField>[0]> = {}) {
  const props = {
    query: '',
    onQueryChange: vi.fn(),
    inScope: [] as Match[],
    elsewhere: [] as Match[],
    onJump: vi.fn(),
    onSelectFirst: vi.fn(),
    ...overrides,
  };

  render(<OrgSearchField {...props} />);

  return props;
}

describe('OrgSearchField', () => {
  it('says how many people matched', () => {
    renderField({ query: 'hop', inScope: [match(1, 'Grace', 'Hopper', 'Sales')] });

    expect(screen.getByText(/1 person match/)).toBeInTheDocument();
  });

  it('announces the count to a screen reader as it changes', () => {
    // Vurgulanan dugumler ekran okuyucu kullanan biri icin hicbir sey ifade
    // etmez; sayi CANLI bildirilmeli.
    const { container } = render(
      <OrgSearchField
        query="hop"
        onQueryChange={vi.fn()}
        inScope={[match(1, 'Grace', 'Hopper', 'Sales')]}
        elsewhere={[]}
        onJump={vi.fn()}
        onSelectFirst={vi.fn()}
      />,
    );

    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull();
  });

  it('says nobody matched instead of staying silent', () => {
    // Bos bir ekran "arama bozuk" ile "kimse yok" arasindaki farki gizlerdi.
    renderField({ query: 'zzzz' });

    expect(screen.getByText('Nobody matches that')).toBeInTheDocument();
  });

  it('stays quiet while the query is too short to mean anything', () => {
    renderField({ query: 'a' });

    expect(screen.queryByText(/match/)).not.toBeInTheDocument();
  });

  it('offers a match in another department as an explicit jump', () => {
    // SEMA KENDILIGINDEN DEGISMEZ. Kayitli kural: "birincil etkilesim,
    // gorselin kendisini yeniden duzenlememelidir" -- yeniden duzenleme
    // ayri ve ACIK bir denetim ister ve bu cip odur.
    const props = renderField({
      query: 'add',
      elsewhere: [match(3, 'Mary', 'Addison', 'Accounting')],
    });

    expect(props.onJump).not.toHaveBeenCalled();
    expect(screen.getByText(/Mary Addison · Accounting/)).toBeInTheDocument();
  });

  it('jumps only when the chip is actually clicked', async () => {
    const props = renderField({
      query: 'add',
      elsewhere: [match(3, 'Mary', 'Addison', 'Accounting')],
    });

    await userEvent.click(screen.getByText(/Mary Addison · Accounting/));

    expect(props.onJump).toHaveBeenCalledWith(
      expect.objectContaining({ department: 'Accounting' }),
    );
  });

  it('separates what is here from what is elsewhere', () => {
    // Iki sayiyi tek bir toplamda birlestirmek, kullaniciya "3 kisi var" der
    // ama ekranda 1 tane gorunur -- sayinin kendisi yaniltici olurdu.
    renderField({
      query: 'ac',
      inScope: [match(1, 'Grace', 'Hopper', 'Sales')],
      elsewhere: [match(3, 'Mary', 'Addison', 'Accounting')],
    });

    expect(screen.getByText(/1 here, 1 in another department/)).toBeInTheDocument();
  });

  it('selects the first match on Enter', () => {
    const props = renderField({ query: 'hop' });

    const box = screen.getByLabelText('Search people');
    box.focus();

    return userEvent.keyboard('{Enter}').then(() => {
      expect(props.onSelectFirst).toHaveBeenCalled();
    });
  });

  it('clears the box on Escape without touching the page', async () => {
    // Escape yalnizca KUTUYU temizler. Sayfanin herhangi bir yerinde Escape
    // aramayi silmemeli -- ayni karar personel listesinin kisayolunda
    // verilmisti.
    const props = renderField({ query: 'hop' });

    screen.getByLabelText('Search people').focus();
    await userEvent.keyboard('{Escape}');

    expect(props.onQueryChange).toHaveBeenCalledWith('');
  });
});
