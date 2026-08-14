import { useId, useMemo, useState } from 'react';
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
  /** Kapali dugumler; cocuklari cizilmez. */
  collapsed: ReadonlySet<number>;
  onToggle: (node: OrgNode) => void;
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

/** En ince ve en kalin bag (px). */
const LINK_MIN = 1;
const LINK_MAX = 5;

export function OrgBubbleMap({ roots, colors, collapsed, onToggle }: Props) {
  const [hovered, setHovered] = useState<number | null>(null);
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
  // Gradyan kimligi bilesen basina benzersiz olmali; sabit bir id iki sema
  // yan yana geldiginde catisirdi.
  const glowId = useId();

  const layout = useMemo(() => layoutTree(roots, collapsed), [roots, collapsed]);

  if (!layout) return null;

  // Uzerine gelinen kisinin merkeze kadar olan zinciri. Baglar varsayilan
  // olarak neredeyse gorunmez; yol ancak SORULDUGUNDA beliriyor.
  const lit = new Set(
    hovered === null
      ? []
      : layout.nodes.find((entry) => entry.node?.id === hovered)?.ancestorIds ?? [],
  );

  const centre = layout.size / 2;
  const spin = reduceMotion ? 'none' : `orbitSpin ${ORBIT_PERIOD}s linear infinite`;
  const spinBack = reduceMotion ? 'none' : `orbitSpinBack ${ORBIT_PERIOD}s linear infinite`;

  return (
    <Box
      component="svg"
      viewBox={`0 0 ${layout.size} ${layout.size}`}
      role="img"
      aria-label={`Organisation chart as orbiting layers, ${layout.nodes.length} nodes`}
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
        <radialGradient id={glowId}>
          <stop offset="0%" stopColor="currentColor" stopOpacity={0.12} />
          <stop offset="55%" stopColor="currentColor" stopOpacity={0.03} />
          <stop offset="100%" stopColor="currentColor" stopOpacity={0} />
        </radialGradient>
      </defs>

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
            strokeDasharray="1 9"
            style={{ animationDelay: `${reduceMotion ? 0 : index * DEPTH_DELAY}ms` }}
            sx={{
              stroke: 'currentColor',
              strokeOpacity: 0.16,
              strokeWidth: 1,
              animation: reduceMotion ? 'none' : 'fadeIn 600ms ease both',
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
            reduceMotion={reduceMotion}
            spinBack={spinBack}
            onHover={setHovered}
            onToggle={onToggle}
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
        // Baglar varsayilan olarak neredeyse gorunmez: sema once ASILI
        // katmanlar olarak okunur, bag ancak sorulunca belirir.
        strokeOpacity: onPath ? 1 : (lit.size > 0 ? 0.04 : 0.11),
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
  onHover: (id: number | null) => void;
  onToggle: (node: OrgNode) => void;
}

const DRIFTS = ['driftA', 'driftB', 'driftC', 'driftD'] as const;

function Node({
  entry, hubLabel, colors, hovered, lit, reduceMotion, spinBack, onHover, onToggle,
}: NodeProps) {
  const theme = useTheme();
  const { node, x, y, r, depth, size, hasChildren, collapsed, hidden } = entry;

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
            cursor: hasChildren ? 'pointer' : 'default',
            transition: 'opacity 260ms ease',
            opacity: dimmed ? 0.3 : 1,
            '&:focus-visible': { outline: 'none' },
            '&:focus-visible .node-ring': { opacity: 1 },
          }}
          onMouseEnter={() => node && onHover(node.id)}
          onFocus={() => node && onHover(node.id)}
          onBlur={() => onHover(null)}
          onClick={() => node && hasChildren && onToggle(node)}
          // Ekibi olan dugum bir DUGMEDIR; yaprak tiklanabilir degil, o yuzden
          // odak sirasina da girmez.
          {...(node && hasChildren
            ? {
              role: 'button',
              tabIndex: 0,
              'aria-expanded': !collapsed,
              'aria-label': `${fullName(node)}, ${size - 1} people in the team`,
              onKeyDown: (event: React.KeyboardEvent) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onToggle(node);
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
              opacity: isHovered ? 1 : 0,
              transition: 'opacity 180ms ease',
            }}
          />

          {/* Kapali dugumun disinda kesikli bir halka: "burada dahasi var"
              bilgisini RENKTEN bagimsiz, bicimle tasir. */}
          {collapsed && (
            <Box
              component="circle"
              cx={x}
              cy={y}
              r={r + 4}
              fill="none"
              strokeDasharray="3 4"
              sx={{ stroke: fill, strokeWidth: 1.5, strokeOpacity: 0.9 }}
            />
          )}

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
              {hasChildren
                ? ` (${size - 1} in the team, ${collapsed ? 'closed' : 'open'})`
                : ''}
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

          {/* Kapali dugumde gizlenen kisi sayisi: sessizce kaybetmek, eksik
              oldugunu soylemeyen bir sema uretirdi. */}
          {collapsed && (
            <Box
              component="text"
              x={x}
              y={y + r + (label ? 28 : 15)}
              textAnchor="middle"
              fontSize={10.5}
              sx={{ fill: 'currentColor', fillOpacity: 0.6, pointerEvents: 'none' }}
            >
              {`+${hidden}`}
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
