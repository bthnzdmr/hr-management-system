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
  /**
   * Cizili kapsamin adi: secili departman, yoksa `null` (butun organizasyon).
   *
   * Basliklarda GERCEK ad yaziliyor ("In Sales"), genel bir ifade degil:
   * "burada / baska yerde" ancak neresi oldugunu bilen biri icin anlamli.
   */
  scopeName: string | null;
  /** Cizili kapsamdaki bir eslesmeyi secer; sema yerinde kalir. */
  onSelect: (match: Match) => void;
  /** Kapsam disindaki bir eslesmeye gider: once departmani degistirir. */
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
  query, onQueryChange, scopeName, inScope, elsewhere, onSelect, onJump, onSelectFirst,
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

      {/*
        Eslesmeler ADIYLA listeleniyor -- ekrandakiler de, baska departmandakiler de.
        Once yalnizca kapsam DISI olanlar listeleniyordu ve aradaki asimetri
        savunulamazdi: ekrandaki eslesme yalnizca VURGULANIYOR, adi ise ancak
        etiketi sigdiysa gorunuyor. Olculdu: 36 dugumun ~5'inde etiket sigmiyor,
        yani aranan kisi adi hicbir yerde yazmayan bir daire olabiliyordu.
        Yanindaki anahat listesi bunu ortuyordu; o kaldirilinca bosluk acildi.

        Tiklama iki listede FARKLI is yapiyor ve ayrim etikette gorunuyor:
        buradaki kisiyi SECER, oteki once o departmana GECER. Sema
        kendiliginden yeniden duzenlenmez; gecis acik bir denetimdir.
      */}
      {active && inScope.length > 0 && (
        <ChipRow
          // Baslik yalnizca IKI grup birden varken gerekli: tek grup varken
          // "burada / baska yerde" ayrimi zaten yok ve baslik gurultu olurdu.
          heading={elsewhere.length > 0 ? here(scopeName) : null}
          matches={inScope}
          total={inScope.length}
          onPick={onSelect}
          labelOf={(match) => fullName(match.node)}
        />
      )}

      {active && elsewhere.length > 0 && (
        <ChipRow
          // Kapsam disi grup DAIMA basligini tasir -- ekranda hic ic kapsam
          // eslesmesi olmasa bile. Aksi halde baska departmandaki bir kisi,
          // cizili departmandaymis gibi okunur; sikayet tam olarak buydu.
          heading={notHere(scopeName)}
          matches={elsewhere}
          total={elsewhere.length}
          onPick={onJump}
          labelOf={(match) => `${fullName(match.node)} · ${match.department}`}
          away
        />
      )}

    </Stack>
  );
}

/** En fazla kac eslesme cip olarak yazilir. */
const CHIP_LIMIT = 6;

/** "Burada" grubunun basligi; kapsamin GERCEK adiyla. */
function here(scopeName: string | null) {
  return scopeName ? `In ${scopeName}` : 'On this map';
}

/** "Baska yerde" grubunun basligi. */
function notHere(scopeName: string | null) {
  return scopeName
    ? `Not in ${scopeName} — click to go there`
    : 'Elsewhere — click to go there';
}

/**
 * Eslesme cipleri.
 *
 * Iki grup UC kanaldan birden ayriliyor ve bu bilincli: basligin kendisi,
 * cipin bicimi (dolgulu / cerceveli) ve etiketteki departman adi. Tek kanal
 * yeterliydi denemez -- olculdu: yalnizca "· Marketing" ekiyle ayrildiklarinda
 * kullanici baska departmandaki kisiyi cizili departmanda sandi.
 *
 * Kirpma SESSIZ DEGIL: sigmayan sayi acikca yaziliyor. Eksik oldugunu
 * soylemeyen bir liste, eksik listeden kotudur.
 */
function ChipRow({
  heading, matches, total, onPick, labelOf, away = false,
}: {
  heading: string | null;
  matches: Match[];
  total: number;
  onPick: (match: Match) => void;
  labelOf: (match: Match) => string;
  away?: boolean;
}) {
  const hidden = total - Math.min(total, CHIP_LIMIT);

  return (
    <Box>
      {heading && (
        <Typography
          variant="caption"
          sx={{
            display: 'block',
            mb: 0.5,
            color: 'text.secondary',
            fontWeight: 600,
            letterSpacing: '0.04em',
            textTransform: 'uppercase',
          }}
        >
          {heading}
        </Typography>
      )}

      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
        {matches.slice(0, CHIP_LIMIT).map((match) => (
          <Chip
            key={match.node.id}
            size="small"
            // Cizili kapsamdaki eslesme DOLGULU, uzaktaki CERCEVELI: dolgu
            // "bu ekranda", cerceve "burada degil" demek. Ayrim renkle
            // yapilmadi -- renk bu sayfada zaten departmani kodluyor ve
            // ikinci bir anlam yuklemek ikisini de okunmaz kilardi.
            variant={away ? 'outlined' : 'filled'}
            clickable
            onClick={() => onPick(match)}
            label={labelOf(match)}
          />
        ))}

        {hidden > 0 && (
          <Typography variant="caption" sx={{ color: 'text.secondary', alignSelf: 'center' }}>
            +{hidden} more
          </Typography>
        )}
      </Stack>
    </Box>
  );
}
