import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type React from 'react';
import { Box, useMediaQuery, useTheme } from '@mui/material';
import { layoutTree } from './orgTree';
import type { TreeLink, TreeNode } from './orgTree';
import { DEPARTMENT_INK, departmentColors, fullName, initials } from './orgScope';
import type { OrgNode } from '../api/orgChart';

interface Props {
  roots: OrgNode[];
  /** Renk esleme, raf ile ayni olsun diye disaridan verilir. */
  colors: Map<string, string>;
  /** Secili kisi; sagdaki panelle ayni durumu paylasir. */
  selectedId: number | null;
  onSelect: (node: OrgNode) => void;
}

/** Butun sistemin bir tam turu (sn). Yavas: okumayi zorlastirmamali. */
const ORBIT_PERIOD = 240;

/** Dugumlerin kendi yerinde salinim genligi (px) ve sure araligi (sn). */
const DRIFT_AMPLITUDE = 5;
const DRIFT_MIN = 7;
const DRIFT_MAX = 13;

/** Halkanin belirme gecikmesi (ms): sema icten disa dogru kuruluyor. */
const DEPTH_DELAY = 150;

/** Bunun altinda bas harfler sigmaz. */
const INITIALS_FIT_ABOVE = 12;

/** Halka uzerindeki iki nokta arasi (normalize edilmis 100 birimde). */
const RING_DOT_SPACING = 2;

/** En ince ve en kalin bag (px). */
const LINK_MIN = 1;
const LINK_MAX = 5;

export function OrgBubbleMap({ roots, colors, selectedId, onSelect }: Props) {
  const [hovered, setHovered] = useState<number | null>(null);
  /**
   * Ok tuslariyla gezilen dugum.
   *
   * Her dugume `tabIndex` vermek belgelenmis bir ANTI-DESENDIR: 32 kisilik bir
   * semada Tab tusu 32 durak yapar ve kullanici semayi atlayamaz. Bilesik bir
   * bilesende tab sirasina TEK bir eleman girer, icerideki gezinme ok
   * tuslariyla yapilir.
   */
  const [roving, setRoving] = useState<number | null>(null);
  const canvas = useRef<SVGSVGElement>(null);

  // `tabIndex` degistirmek odagi TASIMAZ: imleci de goturmezsek ok tusuna
  // basan kullanicinin odagi eski dugumde kalir ve bir sonraki ok tusu yine
  // oradan hesaplanir.
  useEffect(() => {
    if (roving === null) return;

    canvas.current
      ?.querySelector<SVGGElement>(`[data-node-id="${roving}"]`)
      ?.focus();
  }, [roving]);
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
  // Gradyan kimligi bilesen basina benzersiz olmali; sabit bir id iki sema
  // yan yana geldiginde catisirdi.
  const glowId = useId();
  const starsId = useId();
  const theme = useTheme();

  const layout = useMemo(() => layoutTree(roots), [roots]);

  if (!layout) return null;

  // Uzerine gelinen kisinin merkeze kadar olan zinciri. Baglar varsayilan
  // olarak neredeyse gorunmez; yol ancak SORULDUGUNDA beliriyor.
  // Zincir vurgusu once uzerine GELINENI, o yoksa SECILENI izler: secim
  // kalicidir, hover gecici bir sorudur.
  const traced = hovered ?? selectedId;
  const lit = new Set(
    traced === null
      ? []
      : layout.nodes.find((entry) => entry.node?.id === traced)?.ancestorIds ?? [],
  );

  // Gezinme sirasi cizim sirasidir: merkezden disa, kardesler arka arkaya.
  const order = layout.nodes.filter((entry) => entry.node !== null);
  const current = order.findIndex((entry) => entry.node?.id === roving);
  // Hicbiri secilmediyse tab duragi ILK dugumdur; yoksa cizime hic girilemezdi.
  const tabStop = current === -1 ? order[0]?.node?.id ?? null : roving;

  const move = (from: TreeNode, key: string) => {
    const index = order.findIndex((entry) => entry.node?.id === from.node?.id);

    const target = (() => {
      switch (key) {
        case 'ArrowDown':
          return order[Math.min(index + 1, order.length - 1)];
        case 'ArrowUp':
          return order[Math.max(index - 1, 0)];
        case 'ArrowRight':
          // Ic ice yapida saga gitmek ASAGI inmektir: ilk asta gecer.
          return order.find((entry) => entry.ancestorIds[1] === from.node?.id) ?? from;
        case 'ArrowLeft':
          return order.find((entry) => entry.node?.id === from.ancestorIds[1]) ?? from;
        case 'Home':
          return order[0];
        case 'End':
          return order[order.length - 1];
        default:
          return from;
      }
    })();

    return target;
  };

  const centre = layout.size / 2;
  const still = reduceMotion;
  const spin = still ? 'none' : `orbitSpin ${ORBIT_PERIOD}s linear infinite`;
  const spinBack = still ? 'none' : `orbitSpinBack ${ORBIT_PERIOD}s linear infinite`;

  return (
    <Box
      component="svg"
      ref={canvas}
      viewBox={`0 0 ${layout.size} ${layout.size}`}
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
        '@keyframes orbitSpin': {
          from: { transform: 'rotate(0deg)' },
          to: { transform: 'rotate(360deg)' },
        },
        '@keyframes orbitSpinBack': {
          from: { transform: 'rotate(0deg)' },
          to: { transform: 'rotate(-360deg)' },
        },
        // Salinim, dugumu kendi yerinde tutar: yer degistirmez, ASILI durur.
        '@keyframes driftA': {
          '0%, 100%': { transform: 'translate(0px, 0px)' },
          '50%': { transform: `translate(0px, ${-DRIFT_AMPLITUDE}px)` },
        },
        '@keyframes driftB': {
          '0%, 100%': { transform: 'translate(0px, 0px)' },
          '50%': { transform: `translate(${DRIFT_AMPLITUDE}px, ${DRIFT_AMPLITUDE * 0.6}px)` },
        },
        '@keyframes driftC': {
          '0%, 100%': { transform: 'translate(0px, 0px)' },
          '50%': { transform: `translate(${-DRIFT_AMPLITUDE}px, ${DRIFT_AMPLITUDE * 0.5}px)` },
        },
        '@keyframes driftD': {
          '0%, 100%': { transform: 'translate(0px, 0px)' },
          '50%': { transform: `translate(${DRIFT_AMPLITUDE * 0.7}px, ${-DRIFT_AMPLITUDE * 0.8}px)` },
        },
        '@keyframes fadeIn': { from: { opacity: 0 }, to: { opacity: 1 } },
        // HAREKETLI HEDEF sorunu: donen bir seyi tiklamak zordur. Isaretcinin
        // veya klavye odaginin girdigi an butun donme DURUR.
        '&:hover .orbit-system, &:focus-within .orbit-system': {
          animationPlayState: 'paused',
        },
        '&:hover .orbit-counter, &:focus-within .orbit-counter': {
          animationPlayState: 'paused',
        },
      }}
      onMouseLeave={() => setHovered(null)}
    >
      <defs>
        {/* Merkezdeki soluk isik: tuvali bos bir zemin degil, DERINLIGI olan
            bir bosluk gibi okutur. */}
        {/* Isik ON YUKLEMELI duser: opaklıgin cogu %35'te biter. Dogrusal
            bir dusus renkli bir DISK gibi gorunurdu; yayilim kaynagin yaninda
            hizli, kuyrukta uzun soner.

            Dis durak mutlaka stop-opacity=0 olmali: zemin rengiyle
            eslestirmek tema degisince sert bir kenar birakirdi. */}
        <radialGradient id={glowId}>
          <stop offset="0%" stopColor="currentColor" stopOpacity={0.14} />
          <stop offset="35%" stopColor="currentColor" stopOpacity={0.05} />
          <stop offset="70%" stopColor="currentColor" stopOpacity={0.015} />
          <stop offset="100%" stopColor="currentColor" stopOpacity={0} />
        </radialGradient>

        {/* Yildiz alani: gurultuden tek gecisle uretilir. feColorMatrix'in
            son satiri alfayi 9 ile carpip 6 cikarir, yani yalnizca dar bir
            bant hayatta kalir -- geri kalan tamamen seffaflasir. Sabit bir
            filtre bedelini ILK boyamada bir kez oder; donen grubun DISINDA
            durdugu icin her karede yeniden hesaplanmaz. */}
        <filter id={starsId} x="0" y="0" width="100%" height="100%">
          <feTurbulence baseFrequency="0.8" numOctaves={1} seed={7} />
          <feColorMatrix
            values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 9 -6"
          />
        </filter>
      </defs>

      {/* Yildizlar yalnizca KOYU temada: acik zeminde uzay bosluğu anlamsizdir
          ve beyaz noktalar kirlilik gibi okunurdu. */}
      {theme.palette.mode === 'dark' && (
        <rect
          width={layout.size}
          height={layout.size}
          filter={`url(#${starsId})`}
          opacity={0.5}
        />
      )}

      <circle cx={centre} cy={centre} r={centre} fill={`url(#${glowId})`} />

      <Box
        component="g"
        className="orbit-system"
        sx={{
          transformBox: 'view-box',
          transformOrigin: `${centre}px ${centre}px`,
          animation: spin,
        }}
      >
        {/* Yorungeler: her seviye bir halka. Yapiyi tasimazlar, katmani
            gorunur kilarlar. */}
        {layout.rings.map((ring, index) => (
          <Box
            key={ring}
            component="circle"
            cx={centre}
            cy={centre}
            r={ring}
            fill="none"
            // pathLength cevreyi 100'e normalize eder. Olmasaydi sabit bir
            // dasharray her halkada FARKLI nokta araligi verirdi: dis halka
            // seyrek, ic halka sik gorunur ve halkalar ayni ailedenmis gibi
            // okunmazdi.
            pathLength={100}
            strokeDasharray={`0 ${RING_DOT_SPACING}`}
            strokeLinecap="round"
            style={{ animationDelay: `${still ? 0 : index * DEPTH_DELAY}ms` }}
            sx={{
              stroke: 'currentColor',
              strokeOpacity: 0.2,
              // Sifir uzunlukta tire + yuvarlak uc = capi cizgi kalinligi
              // kadar TAM BIR NOKTA.
              strokeWidth: 1.6,
              animation: still ? 'none' : 'fadeIn 600ms ease both',
            }}
          />
        ))}

        {layout.links.map((link) => (
          <Link key={link.id} link={link} largest={layout.largest} lit={lit} />
        ))}

        {layout.nodes.map((entry) => (
          <Node
            key={entry.node?.id ?? 'hub'}
            entry={entry}
            hubLabel={layout.hubLabel}
            colors={colors}
            hovered={hovered}
            lit={lit}
            reduceMotion={still}
            spinBack={spinBack}
            tabStop={entry.node?.id === tabStop}
            selected={entry.node?.id === selectedId}
            onHover={setHovered}
            onSelect={onSelect}
            onMove={(key) => {
              const target = move(entry, key);

              if (target?.node) setRoving(target.node.id);
            }}
          />
        ))}
      </Box>
    </Box>
  );
}

function Link({ link, largest, lit }: { link: TreeLink; largest: number; lit: Set<number> }) {
  // Bag ancak IKI ucu da vurgulanan zincirdeyse yanar; yoksa kardes baglari da
  // aydinlanir ve yol belirsizlesirdi.
  const onPath = lit.size > 0 && link.ancestorIds.every((id) => lit.has(id));

  // Bag, tasidigi kisi sayisiyla kalinlasir: govde kalin, uclar ince.
  const width = LINK_MIN + (LINK_MAX - LINK_MIN) * Math.sqrt(link.size / Math.max(1, largest));

  return (
    <Box
      component="path"
      d={link.path}
      fill="none"
      sx={{
        stroke: (t) => (onPath ? t.palette.primary.main : 'currentColor'),
        // Baglar SOLUK ama gorunur. Once %11'e indirilmisti; olculdu ve
        // fazlaydi: gizlenmis bir bag, yapiyi yalnizca uzerine GELEN kullaniciya
        // verir ve fareyle gelemeyen biri icin hiyerarsi hic okunmaz olur
        // (WCAG 2.1.1, klavye ile isletilebilirlik). Radyal yerlesimde bag
        // zaten kismen artiktir -- seviyeyi yaricap, kardesligi aci soyluyor --
        // bu yuzden %28, d3'un %40 varsayilanindan daha hafif ama yok degil.
        strokeOpacity: onPath ? 1 : (lit.size > 0 ? 0.09 : 0.28),
        strokeWidth: onPath ? width + 1.5 : width,
        strokeLinecap: 'round',
        transition: 'stroke 260ms ease, stroke-opacity 260ms ease, stroke-width 260ms ease',
      }}
    />
  );
}

interface NodeProps {
  entry: TreeNode;
  hubLabel: string | null;
  colors: Map<string, string>;
  hovered: number | null;
  lit: Set<number>;
  reduceMotion: boolean;
  spinBack: string;
  /** Bu dugum tab sirasindaki TEK durak mi? */
  tabStop: boolean;
  selected: boolean;
  onHover: (id: number | null) => void;
  onSelect: (node: OrgNode) => void;
  onMove: (key: string) => void;
}

const DRIFTS = ['driftA', 'driftB', 'driftC', 'driftD'] as const;

/** Cizimin icinde gezinmeyi ustlenen tuslar. */
const NAVIGATION_KEYS = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'];

function Node({
  entry, hubLabel, colors, hovered, lit, reduceMotion, spinBack, tabStop, selected,
  onHover, onSelect, onMove,
}: NodeProps) {
  const theme = useTheme();
  const { node, x, y, r, depth, size, hasChildren } = entry;

  const isHovered = node !== null && hovered === node.id;
  const onPath = node !== null && lit.has(node.id);
  // Zincir vurgulanirken digerleri SONMEZ, yalnizca geri cekilir: bir yolu
  // karsilastirabilmek icin cevresinin gorunur kalmasi gerekir.
  const dimmed = lit.size > 0 && !onPath;

  const label = node ? fullName(node) : hubLabel;
  const fill = node
    ? colors.get(node.departmentName) ?? theme.palette.primary.main
    : theme.palette.background.paper;
  const ink = DEPARTMENT_INK[theme.palette.mode];

  // Salinim deterministik olarak dagitilir: hepsi ayni anda ayni yone gitseydi
  // sema nefes alan tek bir kutle gibi gorunur, ASILI degil.
  const seed = node?.id ?? 0;
  const drift = DRIFTS[seed % DRIFTS.length];
  const period = DRIFT_MIN + (seed % (DRIFT_MAX - DRIFT_MIN));

  return (
    <Box
      component="g"
      className="orbit-counter"
      sx={{
        // Sistem donerken dugum KENDI EKSENINDE ters doner; boylece isim
        // yatay kalir. Ters cevirmeseydik semanin yarisi bas asagi okunurdu.
        transformBox: 'view-box',
        transformOrigin: `${x}px ${y}px`,
        animation: spinBack,
      }}
    >
      <Box
        component="g"
        style={{
          animationDuration: `${period}s`,
          // Negatif gecikme: bileşen kurulur kurulmaz salinimin ORTASINDAN
          // baslar, yani hepsi ayni noktadan hareket etmez.
          animationDelay: `${-(seed % period)}s`,
        }}
        sx={{
          animationName: reduceMotion ? 'none' : drift,
          animationTimingFunction: 'ease-in-out',
          animationIterationCount: 'infinite',
        }}
      >
        <Box
          component="g"
          sx={{
            cursor: 'pointer',
            transition: 'opacity 260ms ease',
            opacity: dimmed ? 0.3 : 1,
            '&:focus-visible': { outline: 'none' },
            '&:focus-visible .node-ring': { opacity: 1 },
          }}
          onMouseEnter={() => node && onHover(node.id)}
          onFocus={() => node && onHover(node.id)}
          onBlur={() => onHover(null)}
          // Tiklamak agaci DEGISTIRMEZ, yalnizca secer: sema yerinde kalir ve
          // ayrinti sagdaki panelde acilir. Yaprak da secilebilir.
          onClick={() => node && onSelect(node)}
          // Agac dugumu: seviye, konum ve kardes sayisi ACIKCA bildirilir.
          // SVG'de DOM ic iceligi hiyerarsiyi ima etmez, ekran okuyucu bunlari
          // baska turlu cikaramaz.
          {...(node
            ? {
              role: 'treeitem',
              'data-node-id': node.id,
              'aria-level': depth + 1,
              'aria-setsize': entry.siblings,
              'aria-posinset': entry.position,
              // Erisilebilir ad kisinin KIMLIGIDIR; ekip buyuklugu <title>'da
              // ve sagdaki panelde duruyor. Ada sayi katmak, secim degistikce
              // adin da degismesi demekti -- bazi ekran okuyucular guncellenen
              // adi hic bildirmez.
              'aria-label': `${fullName(node)}, ${node.jobTitle}`,
              // Tab sirasina TEK bir dugum girer; gerisi ok tuslariyla.
              tabIndex: tabStop ? 0 : -1,
              'aria-selected': selected,
              onKeyDown: (event: React.KeyboardEvent) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onSelect(node);
                  return;
                }

                if (NAVIGATION_KEYS.includes(event.key)) {
                  // Ok tuslari sayfayi KAYDIRMAMALI: odak cizimin icindeyken
                  // gezinme burada olmali.
                  event.preventDefault();
                  onMove(event.key);
                }
              },
            }
            : {})}
        >
          {/* Vurgu halkasi: uzerine gelince ve klavye odaginda gorunur. */}
          <Box
            component="circle"
            className="node-ring"
            cx={x}
            cy={y}
            r={r + 6}
            fill="none"
            sx={{
              stroke: (t) => t.palette.primary.main,
              strokeWidth: 2,
              opacity: isHovered || selected ? 1 : 0,
              // Dogrudan manipulasyon geri bildirimi ANINDA olmali; geciktikce
              // tiklama kaydedilmemis gibi hissettirir.
              transition: 'opacity 90ms ease',
            }}
          />

          <Box
            component="circle"
            cx={x}
            cy={y}
            r={r}
            sx={{
              fill,
              fillOpacity: node === null ? 1 : Math.max(0.66, 1 - depth * 0.1),
              stroke: (t) => (onPath ? t.palette.primary.main : t.palette.background.paper),
              strokeWidth: onPath ? 2.5 : 1.5,
              transition: 'stroke 200ms ease, stroke-width 200ms ease',
            }}
          />

          {node && (
            <title>
              {`${fullName(node)} — ${node.jobTitle}, ${node.departmentName}`}
              {hasChildren ? ` (${size - 1} in the team)` : ''}
            </title>
          )}

          {/* Bas harfler dairenin ICINDE: balon bos bir leke olmaktan cikar. */}
          {node && r > INITIALS_FIT_ABOVE && (
            <Box
              component="text"
              x={x}
              y={y}
              textAnchor="middle"
              dominantBaseline="central"
              fontSize={r * 0.7}
              sx={{ fill: ink, fontWeight: 600, pointerEvents: 'none' }}
            >
              {initials(node)}
            </Box>
          )}

          {/* Isim dairenin ALTINDA ve yatay: donme ters cevrildigi icin her
              konumda duz okunur. */}
          {label && (
            <Box
              component="text"
              x={x}
              y={y + r + 15}
              textAnchor="middle"
              fontSize={depth === 0 ? 13 : 11.5}
              sx={{
                fill: 'currentColor',
                fontWeight: onPath || depth === 0 ? 600 : 500,
                fillOpacity: onPath || depth === 0 ? 1 : 0.74,
                pointerEvents: 'none',
                transition: 'fill-opacity 220ms ease',
              }}
            >
              {label}
            </Box>
          )}

        </Box>
      </Box>
    </Box>
  );
}

/** Cizimdeki renklerin hangi departmana ait oldugunu soyler. */
export function DepartmentLegend({ names, colors }: { names: string[]; colors: Map<string, string> }) {
  return (
    <Box
      component="ul"
      aria-label="Colour key"
      sx={{ listStyle: 'none', display: 'flex', flexWrap: 'wrap', gap: 1.5, m: 0, p: 0 }}
    >
      {names.map((name) => (
        <Box
          component="li"
          key={name}
          sx={{
            display: 'flex', alignItems: 'center', gap: 0.75, fontSize: 12, color: 'text.secondary',
          }}
        >
          <Box
            aria-hidden
            sx={{
              width: 10, height: 10, borderRadius: '50%', bgcolor: colors.get(name), flexShrink: 0,
            }}
          />
          {name}
        </Box>
      ))}
    </Box>
  );
}

/** Renk esleme; raf ve cizim ayni haritayi paylasir. */
export function useDepartmentColors(names: string[]) {
  const theme = useTheme();

  return useMemo(() => departmentColors(names, theme.palette.mode), [names, theme.palette.mode]);
}

/**
 * Cizimin ekran okuyucuya ve Ctrl+F'e verilen karsiligi.
 *
 * <p>Bir SVG cizimi ic ice <code>&lt;ul&gt;</code> kadar dogal okunmaz. Cizim
 * tek basina birakilsaydi agac yapisi yardimci teknolojide tamamen kaybolurdu;
 * burada gorunmeyen ama DOM'da GERCEK METIN olan bir liste render edilir.
 */
export function OrgOutline({ nodes }: { nodes: OrgNode[] }) {
  return (
    <Box
      sx={{
        position: 'absolute',
        width: 1,
        height: 1,
        overflow: 'hidden',
        clip: 'rect(0 0 0 0)',
        whiteSpace: 'nowrap',
      }}
    >
      <TextTree nodes={nodes} label="Reporting structure" />
    </Box>
  );
}

function TextTree({ nodes, label }: { nodes: OrgNode[]; label?: string }) {
  return (
    <ul aria-label={label}>
      {nodes.map((node) => (
        <li key={node.id}>
          {`${fullName(node)}, ${node.jobTitle}, ${node.departmentName}`}
          {node.reports.length > 0 && <TextTree nodes={node.reports} />}
        </li>
      ))}
    </ul>
  );
}
