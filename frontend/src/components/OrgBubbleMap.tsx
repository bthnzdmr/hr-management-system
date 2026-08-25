import { useMemo, useState } from 'react';
import type React from 'react';
import { Box, useMediaQuery, useTheme } from '@mui/material';
import { DRIFT_AMPLITUDE, LINE_GAP, NAME_SIZE, TITLE_SIZE, layoutTree, truncate } from './orgTree';
import type { TreeLink, TreeNode } from './orgTree';
import { departmentColors, departmentInitials, fullName, initials, isDepartmentNode } from './orgScope';
import type { Swatch } from './orgScope';
import type { OrgNode } from '../api/orgChart';

interface Props {
  roots: OrgNode[];
  /** Renk esleme, raf ile ayni olsun diye disaridan verilir. */
  colors: Map<string, Swatch>;
  /** Secili kisi; sagdaki panelle ayni durumu paylasir. */
  selectedId: number | null;
  onSelect: (node: OrgNode) => void;
  /** Departman balonuna tiklandiginda; kisi secmekten AYRI bir eylemdir. */
  onSelectDepartment?: (department: string) => void;
  /**
   * Aramanin esletirdigi kisiler; `null` ise arama YAPILMIYOR demektir.
   *
   * Bos bir kume ile `null` AYNI SEY DEGIL: birincisi "arandi, kimse yok"
   * (her sey geri cekilir), ikincisi "aranmadi" (hicbir sey degismez).
   */
  matchedIds?: Set<number> | null;
  /**
   * Merkezde kisi yoksa orada duran isaret ve tam adi.
   *
   * Once sabit "HR" yaziliydi ve kapsamdan HABERSIZDI: bir departmana
   * gecildiginde de ayni harfler duruyordu, yani merkez neyin merkezi oldugunu
   * soylemiyordu.
   */
  hub: { mark: string; name: string; swatch?: Swatch };
  /** Tek kok olsa bile merkeze kapsamin isaretini koy. */
  alwaysHub?: boolean;
}

/** Salinim yonleri; hepsi ayni yone gitseydi sema tek bir kutle gibi olurdu. */
const DRIFTS = ['driftA', 'driftB', 'driftC', 'driftD'] as const;

/**
 * Salinim suresi araligi (sn).
 *
 * Kisaltildi. Hareketin FARK EDILIR olmasi genlikten cok hiza baglidir ve hiz,
 * genligin aksine cakisma payina hicbir sey odemez -- genligi buyutmek etiket
 * kaybettirir, sureyi kisaltmak bedava.
 */
const DRIFT_MIN = 4;
const DRIFT_MAX = 8;

/** Dairenin ic dolgusunun opakligi; renk KENARDA, govdede degil. */
const DISC_TINT = 0.16;

/** Departman balonu bir ARA katman: kisiden agir, merkezden hafif. */
const DEPARTMENT_TINT = 0.3;

/** Zincir vurgulanirken digerlerinin opakligi. */
const DIMMED = 0.55;

export function OrgBubbleMap({
  roots, colors, selectedId, onSelect, onSelectDepartment, hub, alwaysHub = false,
  matchedIds = null,
}: Props) {
  const [hovered, setHovered] = useState<number | null>(null);
  const still = useMediaQuery('(prefers-reduced-motion: reduce)');

  const layout = useMemo(() => layoutTree(roots, alwaysHub), [roots, alwaysHub]);

  if (!layout) return null;

  // Uzerine gelinen kisinin merkeze kadar olan zinciri.
  const traced = hovered ?? selectedId;
  const lit = new Set(
    traced === null
      ? []
      : layout.nodes.find((entry) => entry.node?.id === traced)?.ancestorIds ?? [],
  );

  // Tab sirasina TEK bir dugum girer: secili varsa o, yoksa ilki.
  const first = layout.nodes.find((entry) => entry.node !== null)?.node?.id ?? null;
  const tabStop = selectedId ?? first;

  const { view } = layout;

  return (
    <Box
      component="svg"
      viewBox={`${view.left} ${view.top} ${view.right - view.left} ${view.bottom - view.top}`}
      // role="img" DEGIL: o rol cocuklari presentational yapar ve icindeki
      // dugumler erisilebilirlik agacina hic girmez. Cizim etkilesimli
      // oldugu icin gecilmesi gereken bir AGACTIR.
      role="tree"
      aria-label={`Organisation chart, ${layout.nodes.length} people`}
      sx={{
        width: '100%',
        height: 'auto',
        display: 'block',
        color: 'text.primary',
        // Her dugum KENDI YERINDE kapali bir yorunge cizer.
        //
        // Butun sistemin donmesi denendi ve geri alindi: kati donme dugumler
        // arasindaki YONU degistirir, dolayisiyla cakismasiz yerlestirilmis
        // etiketler donusun yarisinda ust uste binerdi.
        //
        // Yol kapali, ileri-geri DEGIL: ileri-geri bir salinim uclarda
        // duraklar ve hareket "var mi yok mu" belirsiz gorunur. Kapali yoruge
        // her an ayni hizla ilerler. Genlik her iki eksende de
        // DRIFT_AMPLITUDE'u asmaz -- cakisma payinin dayandigi sinir budur.
        '@keyframes driftA': {
          '0%, 100%': { transform: `translate(${DRIFT_AMPLITUDE}px, 0px)` },
          '25%': { transform: `translate(0px, ${DRIFT_AMPLITUDE}px)` },
          '50%': { transform: `translate(${-DRIFT_AMPLITUDE}px, 0px)` },
          '75%': { transform: `translate(0px, ${-DRIFT_AMPLITUDE}px)` },
        },
        // Ters yon: komsular ayni yone akmasin.
        '@keyframes driftB': {
          '0%, 100%': { transform: `translate(${-DRIFT_AMPLITUDE}px, 0px)` },
          '25%': { transform: `translate(0px, ${DRIFT_AMPLITUDE}px)` },
          '50%': { transform: `translate(${DRIFT_AMPLITUDE}px, 0px)` },
          '75%': { transform: `translate(0px, ${-DRIFT_AMPLITUDE}px)` },
        },
        // Yatik elipsler: hepsi cember olsaydi ritim tekduze olurdu.
        '@keyframes driftC': {
          '0%, 100%': { transform: `translate(${DRIFT_AMPLITUDE}px, ${DRIFT_AMPLITUDE * 0.4}px)` },
          '25%': { transform: `translate(${DRIFT_AMPLITUDE * 0.3}px, ${-DRIFT_AMPLITUDE}px)` },
          '50%': { transform: `translate(${-DRIFT_AMPLITUDE}px, ${-DRIFT_AMPLITUDE * 0.4}px)` },
          '75%': { transform: `translate(${-DRIFT_AMPLITUDE * 0.3}px, ${DRIFT_AMPLITUDE}px)` },
        },
        '@keyframes driftD': {
          '0%, 100%': { transform: `translate(${-DRIFT_AMPLITUDE * 0.4}px, ${DRIFT_AMPLITUDE}px)` },
          '25%': { transform: `translate(${DRIFT_AMPLITUDE}px, ${DRIFT_AMPLITUDE * 0.3}px)` },
          '50%': { transform: `translate(${DRIFT_AMPLITUDE * 0.4}px, ${-DRIFT_AMPLITUDE}px)` },
          '75%': { transform: `translate(${-DRIFT_AMPLITUDE}px, ${-DRIFT_AMPLITUDE * 0.3}px)` },
        },
        // Isaretci veya klavye odagi cizime girdigi an hareket DURUR: okumaya
        // gelen birinin altindaki zemin oynamamali.
        '&:hover .drift, &:focus-within .drift': { animationPlayState: 'paused' },
      }}
      onMouseLeave={() => setHovered(null)}
    >
      {layout.links.map((link) => (
        <Link key={link.id} link={link} lit={lit} />
      ))}

      {layout.nodes.map((entry) => (
        <Node
          key={entry.node?.id ?? 'hub'}
          entry={entry}
          showHub={layout.hub}
          hub={hub}
          colors={colors}
          hovered={hovered}
          lit={lit}
          matchedIds={matchedIds}
          still={still}
          tabStop={entry.node?.id === tabStop}
          selected={entry.node?.id === selectedId}
          onHover={setHovered}
          onSelect={onSelect}
          onSelectDepartment={onSelectDepartment}
        />
      ))}
    </Box>
  );
}

function Link({ link, lit }: { link: TreeLink; lit: Set<number> }) {
  // Bag ancak IKI ucu da vurgulanan zincirdeyse yanar; yoksa kardes baglari da
  // aydinlanir ve yol belirsizlesirdi.
  const onPath = lit.size > 0 && link.ancestorIds.every((id) => lit.has(id));

  return (
    <Box
      component="path"
      d={link.path}
      fill="none"
      sx={{
        stroke: (t) => (onPath ? t.palette.primary.main : 'currentColor'),
        // Kalinlik artik alt agac buyuklugune gore DEGISMIYOR. Kalin govdeler
        // semayi bir boru sistemine cevirip gozu daireden uzaklastiriyordu;
        // ekip buyuklugunu zaten dairenin ALANI soyluyor.
        strokeOpacity: onPath ? 1 : (lit.size > 0 ? 0.1 : 0.28),
        strokeWidth: onPath ? 2.5 : 1.4,
        strokeLinecap: 'round',
        transition: 'stroke 200ms ease, stroke-opacity 200ms ease, stroke-width 200ms ease',
      }}
    />
  );
}

interface NodeProps {
  entry: TreeNode;
  /** Merkezde kisi yok mu? O zaman orada kapsamin isareti durur. */
  showHub: boolean;
  hub: { mark: string; name: string; swatch?: Swatch };
  colors: Map<string, Swatch>;
  hovered: number | null;
  lit: Set<number>;
  /** Arama eslesmeleri; `null` ise arama yapilmiyor. */
  matchedIds: Set<number> | null;
  still: boolean;
  /** Bu dugum tab sirasindaki TEK durak mi? */
  tabStop: boolean;
  selected: boolean;
  onHover: (id: number | null) => void;
  onSelect: (node: OrgNode) => void;
  onSelectDepartment?: (department: string) => void;
}

function Node({
  entry, showHub, hub, colors, hovered, lit, matchedIds, still, tabStop, selected,
  onHover, onSelect, onSelectDepartment,
}: NodeProps) {
  const theme = useTheme();
  const { node, x, y, r, depth, size, hasChildren, label: box } = entry;

  const isHovered = node !== null && hovered === node.id;
  const onPath = node !== null && lit.has(node.id);
  // Zincir vurgulanirken digerleri SONMEZ, yalnizca geri cekilir: bir yolu
  // karsilastirabilmek icin cevresinin gorunur kalmasi gerekir. Deger bir ara
  // %30'a dusmustu; hem karsilastirma kayboluyor hem de grafik ogeleri icin
  // istenen 3:1 kontrast (WCAG 1.4.11) o opaklikta tutmuyor.
  // Iki geri cekme sebebi var ve ayni kanali paylasiyorlar: zincir
  // vurgulamasi ve arama. Arama aktifken zincir SUS OLUR -- kullanici o an
  // "kim eslesiyor" sorusunu soruyor, "bu kisi kime bagli" sorusunu degil.
  const searching = matchedIds !== null;
  const dimmed = searching
    ? !(node !== null && matchedIds.has(node.id))
    : lit.size > 0 && !onPath;

  const swatch = node ? colors.get(node.departmentName) : undefined;

  const isHub = node === null && showHub;
  const isDepartment = node !== null && isDepartmentNode(node);

  // Uc kademeli agirlik: merkez DOLU, departman KALIN HALKA, kisi ince halka.
  //
  // Once departmanlar da dolu ciziliyordu ve ekranda iki "ana balon" gorunuyordu:
  // en kalabalik departmanin capi merkeze yakin ve ikisi de dolu oldugu icin
  // hangisinin merkez oldugu anlasilmiyordu. TEK dolu disk olmasi, merkezi
  // renkle degil BICIMLE ayirir.
  const solid = isHub;
  const accent = isHub
    ? (hub.swatch?.fill ?? theme.palette.text.primary)
    : (swatch?.fill ?? theme.palette.text.secondary);
  const ink = isHub
    ? (hub.swatch?.ink ?? theme.palette.background.paper)
    : (swatch?.ink ?? theme.palette.background.paper);

  const choose = () => {
    if (!node) return;
    // Departman balonu bir kisi DEGIL: tiklamak o departmana iner, rafta
    // yapilan secimin aynisi. Kisi gibi secilebilir olsaydi panel olmayan bir
    // kisiyle dolardi.
    if (isDepartment) onSelectDepartment?.(node.departmentName);
    else onSelect(node);
  };

  // Salinim deterministik olarak dagitilir ve NEGATIF gecikmeyle baslar:
  // hepsi ayni anda ayni noktadan hareket etseydi sema nefes alan tek bir
  // kutle gibi gorunur, asili degil.
  const seed = node?.id ?? 0;
  const period = DRIFT_MIN + (seed % (DRIFT_MAX - DRIFT_MIN));

  return (
    <Box
      component="g"
      className="drift"
      style={{ animationDuration: `${period}s`, animationDelay: `${-(seed % period)}s` }}
      sx={{
        animationName: still ? 'none' : DRIFTS[seed % DRIFTS.length],
        // `linear`: kapali bir yorungede sabit hiz. `ease-in-out` her ara
        // duraga yaklasirken yavaslar ve hareket kesik kesik gorunurdu.
        animationTimingFunction: 'linear',
        animationIterationCount: 'infinite',
      }}
    >
    <Box
      component="g"
      sx={{
        cursor: node ? 'pointer' : 'default',
        transition: 'opacity 200ms ease',
        opacity: dimmed ? DIMMED : 1,
        '&:focus-visible': { outline: 'none' },
        '&:focus-visible .node-ring': { opacity: 1 },
      }}
      onMouseEnter={() => node && onHover(node.id)}
      onFocus={() => node && onHover(node.id)}
      onBlur={() => onHover(null)}
      // Bir KISIYE tiklamak agaci degistirmez, yalnizca secer: sema yerinde
      // kalir ve ayrinti sagdaki panelde acilir.
      onClick={choose}
      {...(node
        ? {
          role: 'treeitem',
          'aria-level': depth + 1,
          'aria-setsize': entry.siblings,
          'aria-posinset': entry.position,
          // Erisilebilir ad kisinin KIMLIGIDIR; ekip buyuklugu <title>'da ve
          // sagdaki panelde duruyor. Ada degisken bir sayi katmak, secim
          // degistikce adin da degismesi demekti ve bazi ekran okuyucular
          // guncellenen erisilebilir adi hic bildirmez.
          'aria-label': `${fullName(node)}, ${node.jobTitle}`,
          tabIndex: tabStop ? 0 : -1,
          'aria-selected': selected,
          onKeyDown: (event: React.KeyboardEvent) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              choose();
            }
          },
        }
        : {})}
    >
      {/* Secim ve uzerine gelme AYRI kanallar: secim kalin ve kalici bir
          cerceve, uzerine gelme gecici bir ince halka. Ikisi ayni kanali
          kullansaydi secili dugum isaretcinin altindakinden ayirt edilemezdi. */}
      <Box
        component="circle"
        className="node-ring"
        cx={x}
        cy={y}
        r={r + 7}
        fill="none"
        sx={{
          stroke: (t) => t.palette.primary.main,
          strokeWidth: selected ? 2.5 : 1.5,
          opacity: selected ? 1 : (isHovered ? 0.7 : 0),
          // Dogrudan manipulasyon geri bildirimi ANINDA olmali; geciktikce
          // tiklama kaydedilmemis gibi hissettirir.
          transition: 'opacity 90ms ease',
        }}
      />

      {/* Renk KENARDA duruyor, govdede degil.
          Onceden daire doymus bir renkle DOLUYDU ve sayfa bir seker kavanozu
          gibi okunuyordu. Ciddiyet, vurgu renginin tonundan cok kapladigi
          ALANDAN gelir -- ayni kil rengi iki piksellik bir cizgide kurumsal,
          bir diski kaplayinca oyuncak duruyor. */}
      <Box
        component="circle"
        cx={x}
        cy={y}
        r={r}
        sx={{
          fill: accent,
          fillOpacity: solid ? 1 : (isDepartment ? DEPARTMENT_TINT : DISC_TINT),
          stroke: (t) => (onPath ? t.palette.primary.main : accent),
          strokeWidth: onPath ? 3.5 : (isDepartment ? 3.5 : 2),
          transition: 'stroke 200ms ease, stroke-width 200ms ease',
        }}
      />

      {node && (
        <title>
          {`${fullName(node)} — ${node.jobTitle}, ${node.departmentName}`}
          {hasChildren ? ` (${size - 1} in the team)` : ''}
        </title>
      )}

      {/* Merkezde kisi yoksa KAPSAMIN isareti durur -- butun organizasyonda
          "ALL", bir departman secildiginde onun bas harfleri. Tam adi <title>
          tasiyor, cunku iki harf tek basina bir ad degildir. */}
      {isHub && (
        <>
          <title>{hub.name}</title>
          <Box
            component="text"
            x={x}
            y={y}
            textAnchor="middle"
            dominantBaseline="central"
            fontSize={r * 0.6}
            sx={{
              fill: ink,
              fontWeight: 700,
              letterSpacing: '0.04em',
              pointerEvents: 'none',
            }}
          >
            {hub.mark}
          </Box>
        </>
      )}

      {node && (
        <Box
          component="text"
          x={x}
          y={y}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={r * 0.62}
          sx={{
            fill: solid ? ink : 'currentColor',
            fontWeight: solid ? 700 : 600,
            pointerEvents: 'none',
          }}
        >
          {/* Departman balonunda kisi bas harfi anlamsiz olurdu: adin ilk
              harfi ile bos bir soyad "S" verirdi ve "Sales" ile "Software
              Development" ayni gorunurdu. */}
          {isDepartment ? departmentInitials(node.departmentName) : initials(node)}
        </Box>
      )}

      {/* Isim ve unvan HER dugumde.
          Onceden yalnizca uzerine gelince beliriyordu ve sema, kimligi
          olmayan lekelerden olusuyordu. Yer bulunamayan nadir durumda etiket
          cizilmez; tam ad o zaman da <title>'da, sagdaki panelde ve gorunmeyen
          anahat listesinde durur. */}
      {box && node && (
        <Box component="g" sx={{ pointerEvents: 'none' }}>
          <Box
            component="text"
            x={box.x}
            y={box.y}
            textAnchor={box.anchor}
            fontSize={NAME_SIZE}
            sx={{
              fill: 'currentColor',
              fontWeight: 600,
              paintOrder: 'stroke',
              // Ince bir zemin konturu: etiket bir dalin uzerine dustugunde
              // okunur kalir.
              stroke: (t) => t.palette.background.paper,
              strokeWidth: 4,
              strokeLinejoin: 'round',
            }}
          >
            {truncate(fullName(node), box.width, NAME_SIZE)}
          </Box>

          <Box
            component="text"
            x={box.x}
            y={box.y + LINE_GAP + TITLE_SIZE}
            textAnchor={box.anchor}
            fontSize={TITLE_SIZE}
            sx={{
              fill: (t) => t.palette.text.secondary,
              paintOrder: 'stroke',
              stroke: (t) => t.palette.background.paper,
              strokeWidth: 3.5,
              strokeLinejoin: 'round',
            }}
          >
            {truncate(node.jobTitle, box.width, TITLE_SIZE)}
          </Box>
        </Box>
      )}
    </Box>
    </Box>
  );
}

/** Renk esleme; raf ve cizim ayni haritayi paylasir. */
export function useDepartmentColors(names: string[]) {
  const theme = useTheme();

  return useMemo(() => departmentColors(names, theme.palette.mode), [names, theme.palette.mode]);
}
