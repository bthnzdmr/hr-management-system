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
    // Uc kisi + kisilerin toplandigi departman balonlari.
    expect(named.length).toBeGreaterThan(3);
    expect(named.some((name) => name?.includes('Test Root'))).toBe(true);
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

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: /^Sales/, pressed: false }));

    const items = within(await outline()).getAllByRole('listitem');
    // Departman balonu artik zincirin BASINDA duruyor: kisiler ondan dallanir.
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Sales'),
      expect.stringContaining('Test SalesHead'),
      expect.stringContaining('Test Rep'),
    ]);
    expect(within(await outline()).queryByText(/Test Designer/)).not.toBeInTheDocument();
  });

  it('shows the person you clicked, without touching the chart', async () => {
    // Onceki tasarim tiklandiginda dali KAPATIYORDU; sema her tiklamada
    // degisiyordu. Simdi tiklamak yalnizca seciyor: agac oldugu gibi kalir.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup({ delay: null });
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

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Gamma/ }));

    expect(screen.getByRole('button', { name: 'Test Root' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Test Alpha/ })).toBeInTheDocument();
  });

  it('shows nobody above someone who is at the top', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Root/ }));

    // Ekibi panelde duruyor ama uzerinde kimse yok: yonetici satiri hic
    // cizilmemeli, bos bir satir birakilmamali.
    expect(screen.getByRole('button', { name: 'Test Alpha' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Test Root' })).not.toBeInTheDocument();
  });

  it('lets you walk the organisation from the panel itself', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Gamma/ }));
    await user.click(screen.getByRole('button', { name: /Test Alpha/ }));

    // Alpha secildi: uzerindeki Root ve altindaki Gamma artik panelde
    // tiklanabilir satirlar. Panel kendi basina bir gezinme araci.
    expect(screen.getByRole('button', { name: 'Test Root' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Test Gamma' })).toBeInTheDocument();
    expect(screen.getByText('1 direct')).toBeInTheDocument();
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

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test SalesHead/ }));
    await user.click(screen.getByRole('button', { name: /^Sales/, pressed: false }));

    // Kisi secimi DUSER: baska bir departmanin kisisine ait bir panel
    // anlamsizdir. Departman odakliyken panel bos degil, OZET gosterir.
    expect(screen.getByRole('heading', { name: 'Sales' })).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Where they sit' })).not.toBeInTheDocument();
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

describe('the panel as an ordered path', () => {
  beforeEach(() => vi.clearAllMocks());

  it('marks the selected person as the current location, not a page', async () => {
    // "page" sayfa gezinmesini bildirir; burada gezinilen sey SEMADAKI KONUM.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Alpha/ }));

    const current = screen.getByRole('listitem', { current: 'location' });
    expect(current).toHaveTextContent('Test Alpha');
  });

  it('keeps the chain in order, top of the organisation first', async () => {
    // Sira bilginin KENDISI: zincir tepeden asagi okunur.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('treeitem', { name: /Test Gamma/ }));

    const path = screen.getByRole('list', { name: 'Where they sit' });
    const rows = within(path).getAllByRole('listitem');

    expect(rows[0]).toHaveTextContent('Test Root');
    expect(rows[1]).toHaveTextContent('Test Alpha');
    expect(rows[2]).toHaveTextContent('Test Gamma');
  });
});

describe('selection is reversible', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lets go of the person when you click them again', async () => {
    // Secili bir kisiye tekrar tiklamak onu birakmiyordu; sema o kisinin
    // uzerinde KILITLI kalmis gibi duruyordu.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    const user = userEvent.setup({ delay: null });
    renderPage();

    const alpha = await screen.findByRole('treeitem', { name: /Test Alpha/ });
    await user.click(alpha);
    expect(screen.getByRole('list', { name: 'Where they sit' })).toBeInTheDocument();

    await user.click(screen.getByRole('treeitem', { name: /Test Alpha/ }));
    expect(screen.getByText('Nobody selected')).toBeInTheDocument();
  });

  it('puts one stop in the tab order, not one per person', async () => {
    // Ok tuslari kaldirildi; klavye kullanicisi cizime girer, Enter ile secer
    // ve gezinmeye paneldeki isimlerden devam eder.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    renderPage();

    const items = await screen.findAllByRole('treeitem');
    expect(items.filter((item) => item.getAttribute('tabindex') === '0')).toHaveLength(1);
  });
});
