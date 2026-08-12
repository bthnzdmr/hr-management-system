import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { OrgChartPage } from './OrgChartPage';
import { orgChartApi } from '../api/orgChart';
import type { OrgNode } from '../api/orgChart';

vi.mock('../api/orgChart', () => ({
  orgChartApi: { get: vi.fn() },
}));

function node(id: number, lastName: string, reports: OrgNode[] = []): OrgNode {
  return {
    id,
    firstName: 'Test',
    lastName,
    jobTitle: 'Engineer',
    departmentName: 'Sales',
    depth: 1,
    reports,
  };
}

describe('OrgChartPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('draws the reporting lines as a nested list', async () => {
    // Agacin sekli VERIDEN gelir, koddan degil: bilesen ozyinelemeli.
    vi.mocked(orgChartApi.get).mockResolvedValue({
      roots: [node(1, 'Root', [node(2, 'Alpha', [node(3, 'Gamma')])])],
      placed: 3,
      unreachable: 0,
    });

    render(<OrgChartPage />);

    expect(await screen.findByText('Test Root')).toBeInTheDocument();

    // Ic ice olma iddiasi: Gamma, Alpha'nin ALTINDA olmali. Duz bir liste de
    // uc ismi birden gosterirdi; asil sinanan sey yerlesim.
    const alpha = (await screen.findByText('Test Alpha')).closest('li');
    expect(alpha).not.toBeNull();
    expect(within(alpha as HTMLElement).getByText('Test Gamma')).toBeInTheDocument();
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

    await screen.findByText('Test Root');
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
