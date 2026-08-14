import { useMemo, useState } from 'react';
import type React from 'react';
import { Box, useMediaQuery } from '@mui/material';
import { CANVAS, packForest } from './circlePacking';
import type { PackedCircle } from './circlePacking';
import { fullName, initials } from './orgScope';
import type { OrgNode } from '../api/orgChart';

interface Props {
  roots: OrgNode[];
  /** Icine girilen daire; ust bilesen kirinti yolunu tutar. */
  onDrillDown: (node: OrgNode) => void;
}

/** Bu yariçapin altinda isim sigmaz; yalnizca bas harfler yazilir. */
const NAME_FITS_ABOVE = 46;

/** Bunun altinda hicbir sey yazilmaz. */
const INITIALS_FIT_ABOVE = 15;

/** Her seviyenin gecikmesi (ms): sema disaridan ICERI dogru aciliyor. */
const DEPTH_DELAY = 90;

/** Ayni seviyedeki kardesler arasindaki gecikme (ms). */
const SIBLING_DELAY = 22;

export function OrgBubbleMap({ roots, onDrillDown }: Props) {
  const [hovered, setHovered] = useState<number | null>(null);
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)');

  // Yerlesim yalnizca veri degisince hesaplanir; her hover'da yeniden
  // paketlemek 32 dugumde bile gorulur bir israftir.
  const packed = useMemo(() => packForest(roots), [roots]);

  if (!packed) return null;

  const flat = flatten(packed);

  return (
    <Box
      component="svg"
      viewBox={`0 0 ${CANVAS} ${CANVAS}`}
      role="img"
      aria-label={`Organisation chart as nested circles, ${flat.length} people`}
      sx={{
        width: '100%',
        height: 'auto',
        display: 'block',
        color: 'text.primary',
        // Bir dairenin icine girildiginde tuval degisir; bu gecis kesme
        // yerine yumusatilir.
        '@keyframes bubbleIn': {
          from: { opacity: 0, transform: 'scale(0.82)' },
          to: { opacity: 1, transform: 'scale(1)' },
        },
      }}
      onMouseLeave={() => setHovered(null)}
    >
      {flat.map((circle, index) => (
        <Bubble
          key={circle.node.id}
          circle={circle}
          index={index}
          hovered={hovered}
          reduceMotion={reduceMotion}
          onHover={setHovered}
          onDrillDown={onDrillDown}
        />
      ))}
    </Box>
  );
}

interface BubbleProps {
  circle: PackedCircle;
  index: number;
  hovered: number | null;
  reduceMotion: boolean;
  onHover: (id: number | null) => void;
  onDrillDown: (node: OrgNode) => void;
}

function Bubble({ circle, index, hovered, reduceMotion, onHover, onDrillDown }: BubbleProps) {
  const { node, x, y, r, depth, children } = circle;
  const isLeaf = children.length === 0;
  const isHovered = hovered === node.id;
  // Baska bir daire uzerindeyken geri kalanlar SONMEZ, yalnizca geri cekilir:
  // karsilastirma yapabilmek icin cevrenin gorunur kalmasi gerekir.
  const dimmed = hovered !== null && !isHovered;

  const delay = reduceMotion ? 0 : depth * DEPTH_DELAY + index * SIBLING_DELAY;

  return (
    <Box
      component="g"
      // Odak dairenin merkezinde olmali: buyume kenardan degil, ortadan.
      style={{
        transformOrigin: `${x}px ${y}px`,
        animationDelay: `${delay}ms`,
      }}
      sx={{
        cursor: isLeaf ? 'default' : 'pointer',
        animation: reduceMotion ? 'none' : 'bubbleIn 420ms cubic-bezier(0.16, 1, 0.3, 1) both',
        transition: 'opacity 180ms ease',
        opacity: dimmed ? 0.55 : 1,
        '& circle': { transition: 'fill 180ms ease, stroke 180ms ease, stroke-width 180ms ease' },
      }}
      onMouseEnter={() => onHover(node.id)}
      onFocus={() => onHover(node.id)}
      onBlur={() => onHover(null)}
      onClick={() => !isLeaf && onDrillDown(node)}
      // Ekibi olan daire bir DUGMEDIR: yalnizca fareyle acilabilseydi klavye
      // kullanicisi semanin icine hic giremezdi. Yaprak tiklanabilir degil,
      // o yuzden odak sirasina da girmez.
      {...(isLeaf ? {} : {
        role: 'button',
        tabIndex: 0,
        'aria-label': `Open the team of ${fullName(node)}, ${circle.size - 1} people`,
        onKeyDown: (event: React.KeyboardEvent) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onDrillDown(node);
          }
        },
      })}
    >
      <Box
        component="circle"
        cx={x}
        cy={y}
        r={r}
        sx={{
          // Derinlik disardan ice dogru koyulasir: tek vurgu renginde kalinir,
          // her seviyeye ayri renk vermek dekoratif gurultu olurdu.
          fill: (t) => `color-mix(in srgb, ${t.palette.primary.main} ${
            isHovered ? 26 + depth * 8 : 7 + depth * 8}%, transparent)`,
          stroke: (t) => (isHovered ? t.palette.primary.main : 'currentColor'),
          strokeOpacity: isHovered ? 0.9 : 0.2,
          strokeWidth: isHovered ? 2 : 1,
        }}
      />

      {/* Tarayici ipucu ve erisilebilir ad; SVG'de dogal karsiligi budur. */}
      <title>
        {`${fullName(node)} — ${node.jobTitle}, ${node.departmentName}`}
        {isLeaf ? '' : ` (${circle.size - 1} in the team, click to open)`}
      </title>

      {/* Yaprakta isim ICERIYE sigmaz, bas harfler yazilir. Ic dugumde merkez
          zaten cocuklarla dolu, o yuzden isim UST KENARA konur. */}
      {isLeaf && r > INITIALS_FIT_ABOVE && (
        <text
          x={x}
          y={y}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={r * 0.5}
          fontWeight={600}
          fill="currentColor"
          pointerEvents="none"
        >
          {initials(node)}
        </text>
      )}

      {!isLeaf && r > NAME_FITS_ABOVE && (
        <text
          x={x}
          y={y - r + 17}
          textAnchor="middle"
          fontSize={13}
          fontWeight={600}
          fill="currentColor"
          pointerEvents="none"
        >
          {fullName(node)}
        </text>
      )}
    </Box>
  );
}

/** Agaci cizim sirasina acar: ebeveyn once, cocuklar ustune. */
function flatten(root: PackedCircle): PackedCircle[] {
  // Sanal kok cizilmez.
  const out: PackedCircle[] = [];

  const visit = (circle: PackedCircle) => {
    out.push(circle);
    circle.children.forEach(visit);
  };

  root.children.forEach(visit);

  return out;
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
