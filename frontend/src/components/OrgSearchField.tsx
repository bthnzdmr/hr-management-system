import { Box, Chip, InputAdornment, Stack, TextField, Typography } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import type { Match } from './orgSearch';
import { MIN_QUERY } from './orgSearch';
import { fullName } from './orgScope';

interface Props {
  query: string;
  onQueryChange: (value: string) => void;
  /** Cizili kapsamda eslesenler; semada zaten vurgulaniyorlar. */
  inScope: Match[];
  /** Baska departmanlardaki eslesmeler; ancak TIKLANIRSA oraya gecilir. */
  elsewhere: Match[];
  onJump: (match: Match) => void;
  onSelectFirst: () => void;
}

/**
 * Haritada kisi arama.
 *
 * Eslesme semada YENI BIR ISARETLE gosterilmiyor: iki kanal zaten dolu
 * (secim = dolgu + kalin cerceve, uzerine gelme = ince cerceve). Bunun yerine
 * eslesmeyenler GERI CEKILIYOR -- ayni desen daire paketleme surumunden beri
 * var ve orani da olculmus: %55'in altina inince hem karsilastirma kayboluyor
 * hem de grafik ogeleri icin 3:1 kontrast sarti (WCAG 1.4.11) tutmuyor.
 */
export function OrgSearchField({
  query, onQueryChange, inScope, elsewhere, onJump, onSelectFirst,
}: Props) {
  const active = query.trim().length >= MIN_QUERY;
  const total = inScope.length + elsewhere.length;

  return (
    <Stack spacing={1}>
      <TextField
        size="small"
        fullWidth
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            onSelectFirst();
          }
          // Escape KUTUYU temizler, sayfayi degil. Sayfanin herhangi bir
          // yerinde Escape aramayi silmemeli -- ayni karar personel
          // listesinin kisayolunda verilmisti.
          if (event.key === 'Escape') onQueryChange('');
        }}
        placeholder="Find someone"
        label="Search people"
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          },
        }}
      />

      {/* Sonuc sayisi CANLI bildirilir: ekran okuyucu kullanan biri icin
          vurgulanan dugumler hicbir sey ifade etmez. */}
      <Box aria-live="polite" sx={{ minHeight: 20 }}>
        {active && (
          <Typography variant="caption" color="text.secondary">
            {total === 0
              ? 'Nobody matches that'
              : `${total} ${total === 1 ? 'person' : 'people'} match`}
            {total > 0 && elsewhere.length > 0
              && ` · ${inScope.length} here, ${elsewhere.length} in another department`}
          </Typography>
        )}
      </Box>

      {/* Kapsam disi eslesmeler. Sema KENDILIGINDEN degismez; buradaki
          tiklama, yeniden duzenlemeyi isteyen ACIK denetimdir. */}
      {active && elsewhere.length > 0 && (
        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
          {elsewhere.slice(0, 6).map((match) => (
            <Chip
              key={match.node.id}
              size="small"
              variant="outlined"
              clickable
              onClick={() => onJump(match)}
              label={`${fullName(match.node)} · ${match.department}`}
            />
          ))}
        </Stack>
      )}
    </Stack>
  );
}
