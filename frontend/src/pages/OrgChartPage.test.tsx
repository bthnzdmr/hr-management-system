import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
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

/** Panel tam kayda baglanti verdigi icin yonlendirici baglami sart. */
function renderPage() {
  return render(
    <MemoryRouter>
      <OrgChartPage />
    </MemoryRouter>,
  );
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

    renderPage();

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

    const { container } = renderPage();

    await screen.findByRole('tree', { name: /Organisation chart/ });
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
    renderPage();

    await user.click(await screen.findByRole('button', { name: /^Sales/ }));

    const items = within(await outline()).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Test SalesHead'),
      expect.stringContaining('Test Rep'),
    ]);
    expect(within(await outline()).queryByText(/Test Designer/)).not.toBeInTheDocument();
  });

  it('shows the person you clicked, without touching the chart', async () => {
    // Onceki tasarim tiklandiginda dali KAPATIYORDU; sema her tiklamada
    // degisiyordu. Simdi tiklamak yalnizca seciyor: agac oldugu gibi kalir.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    const { container } = renderPage();

    // Sayim CIZIM YUKLENDIKTEN sonra alinmali; once alinsaydi sifir olur ve
    // iddia hicbir seyi sinamazdi.
    const alpha = await screen.findByRole('treeitem', { name: /Test Alpha/ });
    const before = container.querySelectorAll('title').length;

    await user.click(alpha);

    expect(screen.getByRole('heading', { name: 'Test Alpha' })
      ?? screen.getByText('Test Alpha')).toBeInTheDocument();
    // Cizimde tek bir dugum bile kaybolmadi veya eklenmedi.
    expect(container.querySelectorAll('title')).toHaveLength(before);
  });

  it('shows the whole chain up to the top, not just the direct manager', async () => {
    // "Bu kisi organizasyonun neresinde" sorusu yalnizca dogrudan yoneticiyle
    // cevaplanamaz.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Gamma/ }));

    expect(screen.getByRole('button', { name: 'Test Root' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Test Alpha/ })).toBeInTheDocument();
  });

  it('says so when the selected person is at the top', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Root/ }));

    expect(screen.getByText(/sits at the top of the chart/)).toBeInTheDocument();
  });

  it('lets you walk the organisation from the panel itself', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Gamma/ }));
    await user.click(screen.getByRole('button', { name: /Test Alpha/ }));

    expect(screen.getByText(/1 direct report/)).toBeInTheDocument();
  });

  it('forgets the selection when the department changes', async () => {
    // Baska bir departmanin kisisini secili birakmak anlamsizdir.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Chief', 'Executive', [
        node(2, 'SalesHead', 'Sales', [node(3, 'Rep', 'Sales')]),
      ])],
      placed: 3,
      unreachable: 0,
    });

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test SalesHead/ }));
    await user.click(screen.getByRole('button', { name: /^Sales/ }));

    expect(screen.getByText('Nobody selected')).toBeInTheDocument();
  });

  it('says how many people the tree could not reach', async () => {
    // Sessizce kaybetmek, EKSIK OLDUGUNU SOYLEMEYEN bir sema uretirdi.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root')],
      placed: 1,
      unreachable: 3,
    });

    renderPage();

    expect(await screen.findByText(/3 active people are not shown/)).toBeInTheDocument();
  });

  it('stays quiet when everybody is placed', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root')],
      placed: 1,
      unreachable: 0,
    });

    renderPage();

    await screen.findByRole('tree', { name: /Organisation chart/ });
    expect(screen.queryByText(/not shown/)).not.toBeInTheDocument();
  });

  it('explains an empty organisation instead of showing a blank card', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({ roots: [], placed: 0, unreachable: 0 });

    renderPage();

    expect(await screen.findByText('No reporting structure yet')).toBeInTheDocument();
  });

  it('shows the error rather than an endless skeleton', async () => {
    vi.mocked(orgChartApi.get).mockRejectedValue(new Error('boom'));

    renderPage();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

});

describe('keyboard navigation', () => {
  beforeEach(() => vi.clearAllMocks());

  it('puts one stop in the tab order, not one per person', async () => {
    // Her dugume tabIndex vermek belgelenmis bir ANTI-DESENDIR: 32 kisilik bir
    // semada Tab tusu 32 durak yapar ve kullanici semayi hic atlayamaz.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    renderPage();

    const items = await screen.findAllByRole('treeitem');
    expect(items).toHaveLength(3);
    expect(items.filter((item) => item.getAttribute('tabindex') === '0')).toHaveLength(1);
  });

  it('walks between people with the arrow keys', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    renderPage();

    const root = await screen.findByRole('treeitem', { name: /Test Root/ });
    root.focus();
    await user.keyboard('{ArrowDown}');

    expect(screen.getByRole('treeitem', { name: /Test Alpha/ })).toHaveFocus();
  });

  it('tells the screen reader which level each person sits on', async () => {
    // SVG'de DOM ic iceligi hiyerarsiyi IMA ETMEZ; seviye acikca bildirilmeli.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    renderPage();

    expect(await screen.findByRole('treeitem', { name: /Test Root/ }))
      .toHaveAttribute('aria-level', '1');
    expect(screen.getByRole('treeitem', { name: /Test Gamma/ }))
      .toHaveAttribute('aria-level', '3');
  });

  it('selects with Enter, so the chart is usable without a mouse', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    renderPage();

    const alpha = await screen.findByRole('treeitem', { name: /Test Alpha/ });
    alpha.focus();
    await user.keyboard('{Enter}');

    expect(alpha).toHaveAttribute('aria-selected', 'true');
  });

  it('walks up the chain with the left arrow', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup();
    renderPage();

    const gamma = await screen.findByRole('treeitem', { name: /Test Gamma/ });
    gamma.focus();
    await user.keyboard('{ArrowLeft}');

    expect(screen.getByRole('treeitem', { name: /Test Alpha/ })).toHaveFocus();
  });
});
