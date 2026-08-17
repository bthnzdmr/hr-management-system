import { Box, Chip, Divider, List, ListItemButton, ListItemText, Stack, Typography } from '@mui/material';
import type { OrgNode } from '../api/orgChart';

/**
 * Departman balonu secildiginde sagdaki panel.
 *
 * Kisi paneliyle ayni isi yapar: semadaki bilgiyi GERCEK METIN olarak verir.
 * Basakanlar tiklanabilir, yani panel ayni zamanda bir gezinme araci --
 * yorunge gorselinin yanindaki dogrusal kacis yolu.
 */
export function DepartmentSummary({ name, headcount, heads, color, onSelect }: {
  name: string;
  headcount: number;
  heads: OrgNode[];
  color?: string;
  onSelect: (person: OrgNode) => void;
}) {
  return (
    <Stack spacing={1.5}>
      <Box>
        <Typography variant="overline" color="text.secondary">Department</Typography>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: color ?? 'text.disabled' }} />
          <Typography variant="h6" component="h2">{name}</Typography>
        </Stack>
        <Chip size="small" label={`${headcount} people`} sx={{ mt: 1 }} />
      </Box>

      <Divider />

      <Box>
        <Typography variant="subtitle2" gutterBottom>
          {/* Baskan SAKLANMAZ, hesaplanir: departmanda olup yoneticisi ayni
              departmanda olmayan kisi. Birden fazla olmasi veri hatasi degil. */}
          {heads.length === 1 ? 'Reports to nobody inside' : 'Report to nobody inside'}
        </Typography>

        {heads.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            Nobody is placed in this department yet.
          </Typography>
        )}

        <List dense disablePadding>
          {heads.map((head) => (
            <ListItemButton key={head.id} onClick={() => onSelect(head)}>
              <ListItemText
                primary={`${head.firstName} ${head.lastName}`}
                secondary={head.jobTitle}
              />
            </ListItemButton>
          ))}
        </List>
      </Box>
    </Stack>
  );
}
