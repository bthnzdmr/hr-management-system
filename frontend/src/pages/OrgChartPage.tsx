import { useEffect, useState } from 'react';
import { Alert, Box, Chip, Paper, Skeleton, Stack, Typography } from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { orgChartApi } from '../api/orgChart';
import type { OrgChart, OrgNode } from '../api/orgChart';
import { errorMessage } from '../api/client';
import { InitialsAvatar } from '../components/InitialsAvatar';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';

/**
 * Tek bir kisi ve altindaki ekip.
 *
 * Ozyinelemeli bir bilesen: agacin sekli veriden geliyor, koddan degil.
 * Girinti ic ice DIV ile degil sol kenar cizgisiyle veriliyor -- boylece
 * derin bir agac yatay kaydirma uretmiyor.
 */
function Branch({ node }: { node: OrgNode }) {
  const hasReports = node.reports.length > 0;

  return (
    <Box component="li" sx={{ listStyle: 'none' }}>
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', py: 0.75 }}>
        <InitialsAvatar firstName={node.firstName} lastName={node.lastName} size={30} />

        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" sx={{ fontWeight: 560, lineHeight: 1.35 }}>
            {node.firstName} {node.lastName}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            {node.jobTitle} · {node.departmentName}
          </Typography>
        </Box>

        {hasReports && (
          <Chip
            size="small"
            label={node.reports.length}
            aria-label={`${node.reports.length} direct reports`}
            sx={{ ml: 0.5 }}
          />
        )}
      </Stack>

      {hasReports && (
        <Box
          component="ul"
          sx={{
            m: 0,
            pl: 2.5,
            ml: 1.85,
            // Girinti ve baglanti tek bir kenar cizgisiyle: her seviye icin
            // ayri bir cizim yapmak gerekmez ve derin agac yatay kaydirmaz.
            borderLeft: 1,
            borderColor: 'divider',
          }}
        >
          {node.reports.map((report) => (
            <Branch key={report.id} node={report} />
          ))}
        </Box>
      )}
    </Box>
  );
}

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
          ? `${chart.placed} ${chart.placed === 1 ? 'person' : 'people'} in the reporting structure`
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

        {chart && chart.roots.length > 0 && (
          <Box component="ul" sx={{ m: 0, p: 0 }}>
            {chart.roots.map((root) => (
              <Branch key={root.id} node={root} />
            ))}
          </Box>
        )}
      </Paper>
    </Stack>
  );
}
