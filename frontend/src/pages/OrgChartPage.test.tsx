import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrgChartPage } from './OrgChartPage';
import { orgChartApi } from '../api/orgChart';
import type { OrgNode } from '../api/orgChart';

vi.mock('../api/orgChart', () => ({
  orgChartApi: { get: vi.fn() },
}));

function node(id: number, lastName: string, department = 'Sales', reports: OrgNode[] = []): OrgNode {
  return {
    id,
    firstName: 'Test',
    lastName,
    jobTitle: 'Engineer',
    departmentName: department,
    depth: 1,
    reports,
  };
}

/** Cizimin yanindaki gorunmez anahat; agac yapisinin erisilebilir karsiligi. */
async function outline() {
  // Kirinti yolu da bir listedir; iddia ANAHATA daraltilir.
  return screen.findByRole('list', { name: 'Reporting structure' });
}

describe('OrgChartPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('keeps the reporting line readable as nested text, not only as a drawing', async () => {
    // Cizim tek basina birakilsaydi agac yapisi ekran okuyucuda ve Ctrl+F'te
    // tamamen kaybolurdu. Iddia GORUNMEYEN listeye yazilir cunku erisilebilirlik
    // garantisini veren sey odur.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root', 'Sales', [node(2, 'Alpha', 'Sales', [node(3, 'Gamma')])])],
      placed: 3,
      unreachable: 0,
    });

    render(<OrgChartPage />);

    const alpha = within(await outline()).getByText(/Test Alpha/).closest('li');
    expect(alpha).not.toBeNull();
    expect(within(alpha as HTMLElement).getByText(/Test Gamma/)).toBeInTheDocument();
  });

  it('draws one circle per person', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root', 'Sales', [node(2, 'Alpha'), node(3, 'Beta')])],
      placed: 3,
      unreachable: 0,
    });

    const { container } = render(<OrgChartPage />);

    await screen.findByRole('img', { name: /branching tree/ });
    // Sanal kok CIZILMEZ: uc kisi, uc daire.
    expect(container.querySelectorAll('circle')).toHaveLength(3);
  });

  it('narrows the map to a department and its own head', async () => {
    // Departman baskani, zincirin yukari dogru departmandan CIKTIGI yerdir.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Chief', 'Executive', [
        node(2, 'SalesHead', 'Sales', [node(3, 'Rep', 'Sales')]),
        node(4, 'Designer', 'Design'),
      ])],
      placed: 4,
      unreachable: 0,
    });

    const user = userEvent.setup();
    render(<OrgChartPage />);

    await user.click(await screen.findByRole('button', { name: /^Sales/ }));

    const items = within(await outline()).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Test SalesHead'),
      expect.stringContaining('Test Rep'),
    ]);
    // Baska departmandaki kisi kapsamda gorunmemeli.
    expect(within(await outline()).queryByText(/Test Designer/)).not.toBeInTheDocument();
  });

  it('opens a team with the keyboard, not only with the mouse', async () => {
    // Yalnizca fareyle acilabilseydi klavye kullanicisi semanin icine hic
    // giremezdi.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root', 'Sales', [node(2, 'Alpha', 'Sales', [node(3, 'Gamma')])])],
      placed: 3,
      unreachable: 0,
    });

    const user = userEvent.setup();
    render(<OrgChartPage />);

    await user.click(await screen.findByRole('button', { name: /Open the team of Test Alpha/ }));

    // Icine girilen kisi artik tepede; kirinti yolu geri donusu tasiyor.
    const items = within(await outline()).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Test Alpha'),
      expect.stringContaining('Test Gamma'),
    ]);
    expect(screen.getByRole('button', { name: 'Whole organisation' })).toBeInTheDocument();
  });

  it('forgets where you had drilled when the department changes', async () => {
    // Baska bir departmanin kisisine ait bir kirinti yolu anlamsizdir.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Chief', 'Executive', [
        node(2, 'SalesHead', 'Sales', [node(3, 'Rep', 'Sales')]),
      ])],
      placed: 3,
      unreachable: 0,
    });

    const user = userEvent.setup();
    render(<OrgChartPage />);

    await user.click(await screen.findByRole('button', { name: /Open the team of Test SalesHead/ }));
    await user.click(screen.getByRole('button', { name: /^Sales/ }));

    expect(screen.queryByRole('link', { name: 'Test SalesHead' })).not.toBeInTheDocument();
  });

  it('says how many people the tree could not reach', async () => {
    // Sessizce kaybetmek, EKSIK OLDUGUNU SOYLEMEYEN bir sema uretirdi.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root')],
      placed: 1,
      unreachable: 3,
    });

    render(<OrgChartPage />);

    expect(await screen.findByText(/3 active people are not shown/)).toBeInTheDocument();
  });

  it('stays quiet when everybody is placed', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root')],
      placed: 1,
      unreachable: 0,
    });

    render(<OrgChartPage />);

    await screen.findByRole('img', { name: /branching tree/ });
    expect(screen.queryByText(/not shown/)).not.toBeInTheDocument();
  });

  it('explains an empty organisation instead of showing a blank card', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({ roots: [], placed: 0, unreachable: 0 });

    render(<OrgChartPage />);

    expect(await screen.findByText('No reporting structure yet')).toBeInTheDocument();
  });

  it('shows the error rather than an endless skeleton', async () => {
    vi.mocked(orgChartApi.get).mockRejectedValue(new Error('boom'));

    render(<OrgChartPage />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
