import { useMemo, useState } from 'react';
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
  /** Icine girilen daire; ust bilesen kirinti yolunu tutar. */
  onDrillDown: (node: OrgNode) => void;
}

/** Her seviyenin gecikmesi (ms): sema merkezden DISARI dogru aciliyor. */
const DEPTH_DELAY = 130;

/** Bir dalin cizilme suresi. */
const BRANCH_DURATION = 440;

/** Daire ile isim arasindaki bosluk. */
const LABEL_GAP = 10;

/** Bunun altinda bas harfler sigmaz. */
const INITIALS_FIT_ABOVE = 13;

/** En ince ve en kalin dal (px). */
const BRANCH_MIN = 1.2;
const BRANCH_MAX = 7;

export function OrgBubbleMap({ roots, colors, onDrillDown }: Props) {
  const [hovered, setHovered] = useState<number | null>(null);
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)');

  // Yerlesim yalnizca veri degisince hesaplanir; her hover'da yeniden
  // hesaplamak 32 dugumde bile gorulur bir israftir.
  const layout = useMemo(() => layoutTree(roots), [roots]);

  if (!layout) return null;

  // Uzerine gelinen kisinin merkeze kadar olan zinciri; dal ve daire vurgusu
  // buna bakar. Bir kisinin nereye bagli oldugu YOLU izlenerek okunur.
  const lit = new Set(
    hovered === null
      ? []
      : layout.nodes.find((entry) => entry.node?.id === hovered)?.ancestorIds ?? [],
  );

  const centre = layout.size / 2;

  return (
    <Box
      component="svg"
      viewBox={`0 0 ${layout.size} ${layout.size}`}
      role="img"
      aria-label={`Organisation chart as a branching tree, ${layout.nodes.length} nodes`}
      sx={{
        width: '100%',
        height: 'auto',
        display: 'block',
        color: 'text.primary',
        '@keyframes branchGrow': {
          from: { strokeDashoffset: 1 },
          to: { strokeDashoffset: 0 },
        },
        '@keyframes nodeIn': {
          from: { opacity: 0, transform: 'scale(0.35)' },
          to: { opacity: 1, transform: 'scale(1)' },
        },
        '@keyframes ringIn': {
          from: { opacity: 0 },
          to: { opacity: 1 },
        },
      }}
      onMouseLeave={() => setHovered(null)}
    >
      {/* Seviye halkalari en arkada: yapiyi tasimazlar, yalnizca "kacinci
          halkadayim" sorusuna sessiz bir cevap verirler. */}
      {layout.rings.map((ring, index) => (
        <Box
          key={ring}
          component="circle"
          cx={centre}
          cy={centre}
          r={ring}
          fill="none"
          strokeDasharray="2 7"
          style={{ animationDelay: `${reduceMotion ? 0 : index * DEPTH_DELAY}ms` }}
          sx={{
            stroke: 'currentColor',
            strokeOpacity: 0.13,
            strokeWidth: 1,
            animation: reduceMotion ? 'none' : 'ringIn 500ms ease both',
          }}
        />
      ))}

      {/* Dallar dugumlerden once cizilir: kesisimlerde cizgi dairenin
          ALTINDAN gecer. */}
      {layout.links.map((link) => (
        <Branch
          key={link.id}
          link={link}
          largest={layout.largest}
          lit={lit}
          reduceMotion={reduceMotion}
        />
      ))}

      {layout.nodes.map((entry) => (
        <Node
          key={entry.node?.id ?? 'hub'}
          entry={entry}
          hubLabel={layout.hubLabel}
          colors={colors}
          hovered={hovered}
          lit={lit}
          reduceMotion={reduceMotion}
          onHover={setHovered}
          onDrillDown={onDrillDown}
        />
      ))}
    </Box>
  );
}

interface BranchProps {
  link: TreeLink;
  largest: number;
  lit: Set<number>;
  reduceMotion: boolean;
}

function Branch({ link, largest, lit, reduceMotion }: BranchProps) {
  // Dal ancak IKI ucu da vurgulanan zincirdeyse yanar; yoksa bir kardesin dali
  // da aydinlanir ve yol belirsizlesirdi.
  const onPath = lit.size > 0 && link.ancestorIds.every((id) => lit.has(id));

  // Dal, tasidigi kisi sayisiyla KALINLASIR: govde kalin, uc dallar ince.
  // Ayni bilgi daire alaninda da var ama burada AKIS olarak okunuyor -- bir
  // dalin nereye gittigi kalinligindan da anlasiliyor.
  const width = BRANCH_MIN
    + (BRANCH_MAX - BRANCH_MIN) * Math.sqrt(link.size / Math.max(1, largest));

  return (
    <Box
      component="path"
      d={link.path}
      fill="none"
      // pathLength uzunlugu 1'e normalize eder; boylece dashoffset 1'den 0'a
      // gidince dal, gercek uzunlugu ne olursa olsun tam olarak uctan uca
      // cizilir. Olmasaydi her yolun boyunu getTotalLength ile olcmek gerekirdi.
      pathLength={1}
      style={{ animationDelay: `${reduceMotion ? 0 : link.depth * DEPTH_DELAY}ms` }}
      sx={{
        stroke: (t) => (onPath ? t.palette.primary.main : 'currentColor'),
        strokeOpacity: onPath ? 1 : (lit.size > 0 ? 0.1 : 0.24),
        strokeWidth: onPath ? width + 1.4 : width,
        strokeLinecap: 'round',
        transition: 'stroke 200ms ease, stroke-opacity 200ms ease, stroke-width 200ms ease',
        strokeDasharray: 1,
        animation: reduceMotion
          ? 'none'
          : `branchGrow ${BRANCH_DURATION}ms cubic-bezier(0.16, 1, 0.3, 1) both`,
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
  onHover: (id: number | null) => void;
  onDrillDown: (node: OrgNode) => void;
}

function Node({
  entry, hubLabel, colors, hovered, lit, reduceMotion, onHover, onDrillDown,
}: NodeProps) {
  const theme = useTheme();
  const { node, x, y, r, depth, angle, size, hasChildren } = entry;

  const isHovered = node !== null && hovered === node.id;
  const onPath = node !== null && lit.has(node.id);
  // Zincir vurgulanirken digerleri SONMEZ, yalnizca geri cekilir: bir yolu
  // karsilastirabilmek icin cevresinin gorunur kalmasi gerekir.
  const dimmed = lit.size > 0 && !onPath;

  const drillable = node !== null && hasChildren;
  const label = node ? fullName(node) : hubLabel;

  const fill = node
    ? colors.get(node.departmentName) ?? theme.palette.primary.main
    : theme.palette.background.paper;
  const ink = DEPARTMENT_INK[theme.palette.mode];

  // Isim halkanin DISINDA durur ve dugumle ayni acida uzanir; sol yaridakiler
  // bas asagi okunmasin diye ters cevrilip saga hizalanir. Cevirmeyi atlamak
  // radyal semanin klasik tuzagi: seman yarisi okunmaz olur.
  const flipped = Math.abs(angle) > 90;
  const base = `rotate(${angle} ${x} ${y}) translate(${x + r + LABEL_GAP} ${y})`;

  return (
    <Box
      component="g"
      style={{
        transformOrigin: `${x}px ${y}px`,
        animationDelay: `${reduceMotion ? 0 : depth * DEPTH_DELAY + BRANCH_DURATION * 0.45}ms`,
      }}
      sx={{
        cursor: drillable ? 'pointer' : 'default',
        animation: reduceMotion ? 'none' : 'nodeIn 400ms cubic-bezier(0.16, 1, 0.3, 1) both',
        transition: 'opacity 200ms ease',
        opacity: dimmed ? 0.32 : 1,
        '&:focus-visible': { outline: 'none' },
        '&:focus-visible .node-ring': { opacity: 1 },
      }}
      onMouseEnter={() => node && onHover(node.id)}
      onFocus={() => node && onHover(node.id)}
      onBlur={() => onHover(null)}
      onClick={() => drillable && onDrillDown(node)}
      // Ekibi olan daire bir DUGMEDIR: yalnizca fareyle acilabilseydi klavye
      // kullanicisi semanin icine hic giremezdi. Yaprak tiklanabilir degil, o
      // yuzden odak sirasina da girmez.
      {...(drillable
        ? {
          role: 'button',
          tabIndex: 0,
          'aria-label': `Open the team of ${fullName(node)}, ${size - 1} people`,
          onKeyDown: (event: React.KeyboardEvent) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              onDrillDown(node);
            }
          },
        }
        : {})}
    >
      {/* Vurgu halkasi: uzerine gelince ve klavye odaginda gorunur. Vurgu
          yalnizca renkle degil BICIMLE de tasinir. */}
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
          opacity: isHovered ? 1 : 0,
          transition: 'opacity 180ms ease',
        }}
      />

      <Box
        component="circle"
        cx={x}
        cy={y}
        r={r}
        sx={{
          fill,
          // Derinlik ARTTIKCA daire seffaflasir: govde tok, uclar hafif.
          // Bilgi tek basina buna binmiyor -- boyut ve konum zaten soyluyor.
          fillOpacity: node === null ? 1 : Math.max(0.62, 1 - depth * 0.12),
          stroke: (t) => (onPath ? t.palette.primary.main : t.palette.background.paper),
          strokeWidth: onPath ? 2.5 : 1.5,
          transition: 'stroke 200ms ease, stroke-width 200ms ease',
        }}
      />

      {node && (
        <title>
          {`${fullName(node)} — ${node.jobTitle}, ${node.departmentName}`}
          {hasChildren ? ` (${size - 1} in the team, open to see them)` : ''}
        </title>
      )}

      {/* Bas harfler dairenin ICINDE: balon bos bir leke olmaktan cikar ve
          kim oldugu isme bakmadan da secilebilir. */}
      {node && r > INITIALS_FIT_ABOVE && (
        <Box
          component="text"
          x={x}
          y={y}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={r * 0.66}
          sx={{ fill: ink, fontWeight: 600, letterSpacing: '0.01em', pointerEvents: 'none' }}
        >
          {initials(node)}
        </Box>
      )}

      {label && (
        <Box
          component="text"
          // Ters cevrilen etiket daireden yine DISARI uzansin diye kendi
          // ekseninde 180 derece dondurulur ve saga hizalanir.
          transform={flipped ? `${base} rotate(180)` : base}
          textAnchor={flipped ? 'end' : 'start'}
          dominantBaseline="central"
          fontSize={depth === 0 ? 14 : 12}
          sx={{
            fill: 'currentColor',
            fontWeight: onPath || depth === 0 ? 600 : 500,
            fillOpacity: onPath || depth === 0 ? 1 : 0.76,
            pointerEvents: 'none',
            transition: 'fill-opacity 200ms ease',
          }}
        >
          {label}
        </Box>
      )}
    </Box>
  );
}

/** Cizimdeki renklerin hangi departmana ait oldugunu soyler. */
export function DepartmentLegend({ names, colors }: { names: string[]; colors: Map<string, string> }) {
  return (
    <Box
      component="ul"
      aria-label="Colour key"
      sx={{
        listStyle: 'none',
        display: 'flex',
        flexWrap: 'wrap',
        gap: 1.5,
        m: 0,
        p: 0,
      }}
    >
      {names.map((name) => (
        <Box
          component="li"
          key={name}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            fontSize: 12,
            color: 'text.secondary',
          }}
        >
          <Box
            aria-hidden
            sx={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              bgcolor: colors.get(name),
              flexShrink: 0,
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

  return useMemo(
    () => departmentColors(names, theme.palette.mode),
    [names, theme.palette.mode],
  );
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
