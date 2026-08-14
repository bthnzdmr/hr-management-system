import { useEffect, useState } from 'react';
import { Alert, Paper, Skeleton, Stack } from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { orgChartApi } from '../api/orgChart';
import type { OrgChart } from '../api/orgChart';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { OrgBubbleMap } from '../components/OrgBubbleMap';

export function OrgChartPage() {
  const [chart, setChart] = useState<OrgChart | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    orgChartApi.get()
      .then((data) => {
        if (active) setChart(data);
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });

    // Temizlik: bilesen sokulduktan sonra gelen cevap durumu yazmasin.
    return () => {
      active = false;
    };
  }, []);

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="Directory"
        title="Org chart"
        description={chart
          ? `${chart.placed} ${chart.placed === 1 ? 'person' : 'people'} — a circle inside another means that person reports to them`
          : 'Reporting lines across the organisation'}
      />

      {/* Ulasilamayan kisiler UYARI degil BILGI olarak gosterilir.
          Yoneticisi pasiflesmis personel agacta yer alamaz ve bu bir veri
          hatasi olmayabilir -- ama sessizce kaybolmasi da olmaz. */}
      {chart && chart.unreachable > 0 && (
        <Alert severity="info">
          {chart.unreachable} active {chart.unreachable === 1 ? 'person is' : 'people are'} not
          shown: their manager has left, so they cannot be connected to anyone above them.
        </Alert>
      )}

      <Paper sx={{ p: { xs: 2, md: 3 } }}>
        {!chart && (
          <Stack spacing={1.5}>
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} variant="rounded" height={40} />
            ))}
          </Stack>
        )}

        {chart && chart.roots.length === 0 && (
          <EmptyState
            icon={<AccountTreeOutlinedIcon />}
            title="No reporting structure yet"
            description="Nobody sits at the top of the organisation, so there is no tree to draw."
          />
        )}

        {chart && chart.roots.length > 0 && <OrgBubbleMap roots={chart.roots} />}
      </Paper>
    </Stack>
  );
}
