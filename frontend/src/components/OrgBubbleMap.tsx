import { useMemo, useState } from 'react';
import type React from 'react';
import { Box, useMediaQuery } from '@mui/material';
import { layoutTree } from './orgTree';
import type { TreeLink, TreeNode } from './orgTree';
import { fullName } from './orgScope';
import type { OrgNode } from '../api/orgChart';

interface Props {
  roots: OrgNode[];
  /** Icine girilen daire; ust bilesen kirinti yolunu tutar. */
  onDrillDown: (node: OrgNode) => void;
}

/** Her seviyenin gecikmesi (ms): sema merkezden DISARI dogru aciliyor. */
const DEPTH_DELAY = 130;

/** Bir dalin cizilme suresi. */
const BRANCH_DURATION = 420;

/** Daire ile isim arasindaki bosluk. */
const LABEL_GAP = 9;

export function OrgBubbleMap({ roots, onDrillDown }: Props) {
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
          from: { opacity: 0, transform: 'scale(0.4)' },
          to: { opacity: 1, transform: 'scale(1)' },
        },
      }}
      onMouseLeave={() => setHovered(null)}
    >
      {/* Dallar once cizilir: dugumler onlarin uzerinde durur ve kesisimlerde
          cizgi dairenin altindan gecer. */}
      {layout.links.map((link) => (
        <Branch key={link.id} link={link} lit={lit} reduceMotion={reduceMotion} />
      ))}

      {layout.nodes.map((entry) => (
        <Node
          key={entry.node?.id ?? 'hub'}
          entry={entry}
          hubLabel={layout.hubLabel}
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

function Branch({
  link, lit, reduceMotion,
}: { link: TreeLink; lit: Set<number>; reduceMotion: boolean }) {
  // Dal ancak IKI ucu da vurgulanan zincirdeyse yanar; yoksa bir kardesin dali
  // da aydinlanir ve yol belirsizlesirdi.
  const onPath = lit.size > 0 && link.ancestorIds.every((id) => lit.has(id));

  return (
    <Box
      component="path"
      d={link.path}
      fill="none"
      // pathLength uzunlugu 1'e normalize eder; boylece dashoffset 1'den 0'a
      // gidince dal, gercek uzunlugu ne olursa olsun tam olarak uctan uca
      // cizilir.
      pathLength={1}
      style={{ animationDelay: `${reduceMotion ? 0 : link.depth * DEPTH_DELAY}ms` }}
      sx={{
        stroke: (t) => (onPath ? t.palette.primary.main : 'currentColor'),
        strokeOpacity: onPath ? 0.95 : (lit.size > 0 ? 0.12 : 0.26),
        strokeWidth: onPath ? 2.2 : 1.4,
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
  hovered: number | null;
  lit: Set<number>;
  reduceMotion: boolean;
  onHover: (id: number | null) => void;
  onDrillDown: (node: OrgNode) => void;
}

function Node({ entry, hubLabel, hovered, lit, reduceMotion, onHover, onDrillDown }: NodeProps) {
  const { node, x, y, r, depth, angle, size, hasChildren } = entry;

  const isHovered = node !== null && hovered === node.id;
  const onPath = node !== null && lit.has(node.id);
  // Zincir vurgulanirken digerleri SONMEZ, yalnizca geri cekilir: bir yolu
  // karsilastirabilmek icin cevresinin gorunur kalmasi gerekir.
  const dimmed = lit.size > 0 && !onPath;

  const drillable = node !== null && hasChildren;
  const label = node ? fullName(node) : hubLabel;

  // Isim halkanin DISINDA durur ve daireyle birlikte doner; sol yaridakiler
  // bas asagi okunmasin diye ters cevrilip saga hizalanir.
  const flipped = Math.abs(angle) > 90;
  const base = `rotate(${angle} ${x} ${y}) translate(${x + r + LABEL_GAP} ${y})`;

  return (
    <Box
      component="g"
      style={{
        transformOrigin: `${x}px ${y}px`,
        animationDelay: `${reduceMotion ? 0 : depth * DEPTH_DELAY + BRANCH_DURATION * 0.5}ms`,
      }}
      sx={{
        cursor: drillable ? 'pointer' : 'default',
        animation: reduceMotion ? 'none' : 'nodeIn 380ms cubic-bezier(0.16, 1, 0.3, 1) both',
        transition: 'opacity 200ms ease',
        opacity: dimmed ? 0.35 : 1,
        '&:focus-visible': { outline: 'none' },
        '&:focus-visible circle': { stroke: (t) => t.palette.primary.main, strokeWidth: 3 },
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
      {/* Uzerine gelince dairenin arkasinda yumusak bir hale: vurgu yalnizca
          renkle degil BICIMLE de tasinir. */}
      {isHovered && (
        <Box
          component="circle"
          cx={x}
          cy={y}
          r={r + 7}
          sx={{ fill: (t) => t.palette.primary.main, fillOpacity: 0.18 }}
        />
      )}

      <Box
        component="circle"
        cx={x}
        cy={y}
        r={r}
        sx={{
          fill: (t) => (node === null
            ? t.palette.background.paper
            : `color-mix(in srgb, ${t.palette.primary.main} ${
              onPath ? 78 : 30 + Math.max(0, 22 - depth * 7)}%, ${t.palette.background.paper})`),
          stroke: (t) => (onPath ? t.palette.primary.main : 'currentColor'),
          strokeOpacity: onPath ? 1 : 0.35,
          strokeWidth: node === null ? 2 : 1.25,
          transition: 'fill 200ms ease, stroke 200ms ease',
        }}
      />

      {node && (
        <title>
          {`${fullName(node)} — ${node.jobTitle}, ${node.departmentName}`}
          {hasChildren ? ` (${size - 1} in the team, open to see them)` : ''}
        </title>
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
            fontWeight: onPath || depth === 0 ? 600 : 500,
            fill: 'currentColor',
            fillOpacity: onPath || depth === 0 ? 1 : 0.78,
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
