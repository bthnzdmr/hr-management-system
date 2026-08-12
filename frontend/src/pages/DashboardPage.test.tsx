import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';
import { dashboardApi } from '../api/dashboard';
import type { DashboardOverview } from '../api/dashboard';

vi.mock('../api/dashboard', () => ({
  dashboardApi: { overview: vi.fn() },
}));

function overview(overrides: Partial<DashboardOverview> = {}): DashboardOverview {
  return {
    headcount: {
      active: 32,
      inactive: 6,
      hiredLast30Days: 1,
      hiredLast90Days: 3,
      leftLast12Months: 6,
      turnoverRate: 18.8,
    },
    byDepartment: [
      { department: 'Software Development', active: 12 },
      { department: 'Sales', active: 6 },
      { department: 'Marketing', active: 0 },
    ],
    turnoverByMonth: Array.from({ length: 12 }).map((_, index) => ({
      month: `2026-${String(index + 1).padStart(2, '0')}`,
      leavers: index === 5 ? 2 : 0,
    })),
    terminationReasons: [
      { reason: 'RESIGNED', count: 3 },
      { reason: 'RETIRED', count: 1 },
    ],
    spanOfControl: { managerCount: 12, averageDirectReports: 2.3, largestTeam: 4 },
    dataQuality: { activeWithoutManager: 5, emptyDepartments: 1 },
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dashboardApi.overview).mockResolvedValue(overview());
  });

  it('asks the server once instead of one request per widget', async () => {
    // Her kutucuk ayri uctan beslenseydi bazilari digerlerinden once doner
    // ve birbiriyle celisen sayilar gorunurdu.
    renderPage();

    expect(await screen.findByText('32')).toBeInTheDocument();
    expect(dashboardApi.overview).toHaveBeenCalledOnce();
  });

  it('shows the turnover rate as a percentage', async () => {
    renderPage();

    expect(await screen.findByText('18.8%')).toBeInTheDocument();
  });

  it('lists departments that have nobody in them', async () => {
    // Bos bir departman panelin gostermesi gereken bir bulgudur; gizlenseydi
    // "neden kimse yok" sorusu hic sorulmazdi.
    renderPage();

    expect(await screen.findByText('Marketing')).toBeInTheDocument();
  });

  it('translates the termination reason into something readable', async () => {
    renderPage();

    expect(await screen.findByText('Resigned')).toBeInTheDocument();
    expect(screen.queryByText('RESIGNED')).not.toBeInTheDocument();
  });

  it('surfaces findings the user can act on', async () => {
    renderPage();

    expect(await screen.findByText('departments have nobody in them')).toBeInTheDocument();
  });

  it('does not flag people at the top of the organisation as a problem', async () => {
    // Bes departman baskaninin yoneticisi yoktur ve bu bir veri eksikligi
    // degildir; uyari olarak gostermek yanlis alarm uretirdi. Ayirt edecek
    // bir bilgi saklamiyoruz, bu yuzden bilgi olarak sunulur.
    renderPage();

    expect(await screen.findByText('report to nobody')).toBeInTheDocument();
    expect(screen.queryByText('active employees have no manager')).not.toBeInTheDocument();
  });

  it('says so when there is nothing to flag', async () => {
    vi.mocked(dashboardApi.overview).mockResolvedValue(
      overview({ dataQuality: { activeWithoutManager: 5, emptyDepartments: 0 } }),
    );

    renderPage();

    expect(await screen.findByText('Nothing to flag right now.')).toBeInTheDocument();
  });

  it('says so when nobody has left yet', async () => {
    vi.mocked(dashboardApi.overview).mockResolvedValue(overview({ terminationReasons: [] }));

    renderPage();

    expect(await screen.findByText('Nobody has left yet.')).toBeInTheDocument();
  });

  it('shows the failure message instead of an empty screen', async () => {
    vi.mocked(dashboardApi.overview).mockRejectedValue(new Error('boom'));

    renderPage();

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument();
  });
});
