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

/**
 * Cizimdeki kisi dugumleri.
 *
 * Erisilebilirlik garantisini artik YALNIZCA cizim tasiyor: yanindaki anahat
 * listesi kaldirildi cunku tasidigi her sey baska yerde vardi -- hiyerarsi
 * cizimin kendi `role="tree"` semantiginde, arama tiklanabilir kutuda,
 * gezinme sagdaki panelde.
 *
 * OLCULDU: butun organizasyon kapsaminda DEPARTMAN balonlari da birer
 * `treeitem`; yalnizca gorunmez merkeze rol verilmiyor. Bir departman
 * secildiginde o katman hic cizilmez, dolayisiyla sayim yalnizca kisilerdir.
 */
function chartPeople() {
  return screen.findAllByRole('treeitem');
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

  it('exposes the reporting depth on the drawing itself', async () => {
    // Cizimin yaninda GORUNUR bir anahat listesi vardi ve hiyerarsinin
    // erisilebilir karsiligini o tasiyor sayiliyordu. Olculdu: cizim zaten tam
    // bir agac -- `role="tree"`, her dugum `treeitem`, seviye/kardes/konum
    // bildiriliyor ve ok tuslariyla geziliyor. Liste bir tekrardi ve silindi;
    // garantiyi tek basina tasiyan sey artik burasi, o yuzden acikca tutuluyor.
    vi.mocked(orgChartApi.get).mockResolvedValue(chain());

    renderPage();

    const root = await screen.findByRole('treeitem', { name: /Test Root/ });
    const alpha = screen.getByRole('treeitem', { name: /Test Alpha/ });
    const gamma = screen.getByRole('treeitem', { name: /Test Gamma/ });

    const level = (node: HTMLElement) => Number(node.getAttribute('aria-level'));

    // MUTLAK deger iddia EDILMEZ: merkez ve departman katmani seviyeyi
    // kaydiriyor ve o bir yerlesim ayrintisi. Tutulan sey garantinin kendisi
    // -- derinlik BILDIRILIYOR ve zincir boyunca artiyor. SVG'de DOM ic
    // iceligi hiyerarsiyi ima etmez, bu yuzden acikca bildirilmesi sart.
    expect(level(root)).toBeGreaterThan(0);
    expect(level(alpha)).toBe(level(root) + 1);
    expect(level(gamma)).toBe(level(alpha) + 1);

    expect(root.closest('[role="tree"]')).not.toBeNull();
  });

  it('gives every person their own node with an accessible name', async () => {
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root', 'Sales', [node(2, 'Alpha'), node(3, 'Beta')])],
      placed: 3,
      unreachable: 0,
    });

    const { container } = renderPage();

    await screen.findByRole('tree', { name: /Organisation chart/ });

    // Butun organizasyonda araya bir DEPARTMAN katmani giriyor, dolayisiyla
    // <title> tasiyan dugumler yalnizca kisiler degil: merkez ve departman
    // balonlari da adlarini tasir. Sayi yerine ADLARIN varligi iddia edilir --
    // sayiyi sabitlemek katman sayisi degistiginde anlamsizca kirilirdi.
    const named = [...container.querySelectorAll('title')].map((t) => t.textContent);

    expect(named).toEqual(expect.arrayContaining([
      'Whole organisation',
      expect.stringContaining('Test Root'),
      expect.stringContaining('Test Alpha'),
      expect.stringContaining('Test Beta'),
    ]));
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

    await user.click(await screen.findByRole('button', { name: /^Sales/ }));

    const people = await chartPeople();
    expect(people.map((item) => item.getAttribute('aria-label'))).toEqual([
      expect.stringContaining('Test SalesHead'),
      expect.stringContaining('Test Rep'),
    ]);
    expect(screen.queryByRole('treeitem', { name: /Test Designer/ })).not.toBeInTheDocument();
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
