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
function outline() {
  // Renk anahtari da bir listedir; iddia ANAHATA daraltilir.
  return screen.findByRole('list', { name: 'Reporting structure' });
}

/** Root -> Alpha -> Gamma zinciri. */
function chain() {
  return {
    roots: [node(1, 'Root', 'Sales', [node(2, 'Alpha', 'Sales', [node(3, 'Gamma')])])],
    placed: 3,
    unreachable: 0,
  };
}

describe('OrgChartPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('keeps the reporting line readable as nested text, not only as a drawing', async () => {
    // Cizim tek basina birakilsaydi agac yapisi ekran okuyucuda ve Ctrl+F'te
    // tamamen kaybolurdu. Iddia GORUNMEYEN listeye yazilir cunku erisilebilirlik
    // garantisini veren sey odur.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    render(<OrgChartPage />);

    const alpha = within(await outline()).getByText(/Test Alpha/).closest('li');
    expect(alpha).not.toBeNull();
    expect(within(alpha as HTMLElement).getByText(/Test Gamma/)).toBeInTheDocument();
  });

  it('gives every person their own node with an accessible name', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root', 'Sales', [node(2, 'Alpha'), node(3, 'Beta')])],
      placed: 3,
      unreachable: 0,
    });

    const { container } = render(<OrgChartPage />);

    await screen.findByRole('img', { name: /orbiting layers/ });
    // <title> yalnizca gercek kisilerde var: yorunge halkalari ve gorunmez
    // merkez sayilmaz. Daireleri saymak bunlari da yakalardi.
    const named = [...container.querySelectorAll('title')].map((t) => t.textContent);
    expect(named).toHaveLength(3);
    expect(named[0]).toContain('Test Root');
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
    expect(within(await outline()).queryByText(/Test Designer/)).not.toBeInTheDocument();
  });

  it('folds a team away with the keyboard, not only with the mouse', async () => {
    // Onceki tasarim tiklandiginda dalin ICINE giriyordu ve her tiklama bir
    // seviye daha derine indiriyordu; kullanici nerede oldugunu kaybediyordu.
    // Acip kapatmak yerinde kalir: baglam hic degismez.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    const { container } = render(<OrgChartPage />);

    const alpha = await screen.findByRole('button', { name: /Test Alpha, 1 people/ });
    expect(alpha).toHaveAttribute('aria-expanded', 'true');

    await user.click(alpha);

    // Gamma cizimden dustu ama Alpha yerinde ve hala acilabilir.
    expect(container.querySelectorAll('title')).toHaveLength(2);
    expect(screen.getByRole('button', { name: /Test Alpha, 1 people/ }))
      .toHaveAttribute('aria-expanded', 'false');
  });

  it('says how many people a folded node is hiding', async () => {
    // Sessizce kaybetmek, eksik oldugunu SOYLEMEYEN bir sema uretirdi.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    render(<OrgChartPage />);

    await user.click(await screen.findByRole('button', { name: /Test Alpha, 1 people/ }));

    expect(screen.getByText('+1')).toBeInTheDocument();
  });

  it('reopens everything in one go', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    const { container } = render(<OrgChartPage />);

    await user.click(await screen.findByRole('button', { name: /Test Alpha, 1 people/ }));
    await user.click(screen.getByRole('button', { name: 'Expand all' }));

    expect(container.querySelectorAll('title')).toHaveLength(3);
  });

  it('forgets what was folded when the department changes', async () => {
    // Baska bir departmanin dugumlerine ait kapali durumu anlamsizdir.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Chief', 'Executive', [
        node(2, 'SalesHead', 'Sales', [node(3, 'Rep', 'Sales')]),
      ])],
      placed: 3,
      unreachable: 0,
    });

    const user = userEvent.setup();
    render(<OrgChartPage />);

    await user.click(await screen.findByRole('button', { name: /Test SalesHead, 1 people/ }));
    await user.click(screen.getByRole('button', { name: /^Sales/ }));

    expect(screen.getByRole('button', { name: /Test SalesHead, 1 people/ }))
      .toHaveAttribute('aria-expanded', 'true');
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

    await screen.findByRole('img', { name: /orbiting layers/ });
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
