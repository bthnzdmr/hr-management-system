import { Box, useTheme } from '@mui/material';
import { packForest } from './circlePacking';
import type { PackedCircle } from './circlePacking';
import type { OrgNode } from '../api/orgChart';

interface Props {
  roots: OrgNode[];
}

/** Bu yariçapın altında isim sigmaz; yalnizca bas harfler yazilir. */
const NAME_FITS_ABOVE = 34;

/** Bunun altinda hicbir sey yazilmaz; daire cok kucuk. */
const INITIALS_FIT_ABOVE = 16;

function initials(node: OrgNode) {
  return `${node.firstName.charAt(0)}${node.lastName.charAt(0)}`.toUpperCase();
}

/**
 * Bir dugumu ve altini cizer.
 *
 * Ozyinelemeli: agacin sekli veriden gelir. Konumlar EBEVEYNE goredir, bu
 * yuzden her seviye kendi <g transform> icinde durur ve mutlak koordinat
 * hesaplamak gerekmez.
 */
function Bubble({ circle, depthColors }: { circle: PackedCircle; depthColors: string[] }) {
  const { node, x, y, r, depth, children } = circle;
  const isLeaf = children.length === 0;
  const fill = depthColors[Math.min(depth, depthColors.length - 1)];

  return (
    <g transform={`translate(${x} ${y})`}>
      <circle
        r={r}
        fill={fill}
        stroke="currentColor"
        strokeOpacity={0.18}
        strokeWidth={1}
      />

      {/* <title> hem tarayici ipucu hem erisilebilir addir; SVG'de dogal karsiligi budur. */}
      <title>
        {`${node.firstName} ${node.lastName} — ${node.jobTitle}, ${node.departmentName}`}
        {children.length > 0 ? ` (${children.length} direct reports)` : ''}
      </title>

      {/* Yaprak: isim dairenin ICINE. Ic dugum: isim UST KENARA, cunku
          merkezi cocuklar kapliyor. */}
      {isLeaf && r > INITIALS_FIT_ABOVE && (
        <text
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={r * 0.42}
          fontWeight={600}
          fill="currentColor"
        >
          {initials(node)}
        </text>
      )}

      {!isLeaf && r > NAME_FITS_ABOVE && (
        <text
          textAnchor="middle"
          y={-r + 14}
          fontSize={12}
          fontWeight={600}
          fill="currentColor"
        >
          {`${node.firstName} ${node.lastName}`}
        </text>
      )}

      {children.map((child) => (
        <Bubble key={child.node.id} circle={child} depthColors={depthColors} />
      ))}
    </g>
  );
}

/**
 * Organizasyon semasi, IC ICE daireler olarak.
 *
 * <p><b>Hiyerarsi nasil korunuyor?</b> <em>Icinde olmak</em> raporlama
 * cizgisidir. Force-directed bir "harita" olsaydi dugumler birbirini iter,
 * kumeler olusur ama "kim kimin ustunde" konum tesadufune kalirdi.
 *
 * <p><b>Erisilebilirlik:</b> Bir SVG cizimi, ic ice <code>&lt;ul&gt;</code>
 * kadar dogal okunmaz. Iki onlem alindi: her daire <code>&lt;title&gt;</code>
 * tasir (ipucu + erisilebilir ad) ve cizimin yaninda ekranda gorunmeyen ama
 * ekran okuyucunun okudugu ic ice bir liste render edilir. Boylece agac yapisi
 * yardimci teknolojide KAYBOLMAZ.
 */
export function OrgBubbleMap({ roots }: Props) {
  const theme = useTheme();
  const packed = packForest(roots);

  if (!packed) return null;

  // Derinlik rengi: disdan ice acilir. Tek vurgu rengiyle kalinir, cunku
  // her seviyeye ayri bir renk vermek dekoratif gurultu olurdu.
  const depthColors = [0, 1, 2, 3, 4].map((depth) =>
    `color-mix(in srgb, ${theme.palette.primary.main} ${8 + depth * 9}%, transparent)`);

  const size = packed.r * 2;

  return (
    <Box>
      <Box
        component="svg"
        viewBox={`${-packed.r} ${-packed.r} ${size} ${size}`}
        role="img"
        aria-label={`Organisation chart as nested circles: ${roots.length} ${
          roots.length === 1 ? 'person reports' : 'people report'} to nobody above them`}
        sx={{
          width: '100%',
          height: 'auto',
          maxHeight: '70vh',
          display: 'block',
          color: 'text.primary',
        }}
      >
        {/* Sanal kok cizilmez; dogrudan cocuklarindan baslanir. */}
        {packed.children.map((child) => (
          <Bubble key={child.node.id} circle={child} depthColors={depthColors} />
        ))}
      </Box>

      {/* Ekranda gorunmez ama ekran okuyucu okur ve DOM'da gercek metin oldugu
          icin sayfa ici arama da bulur. Cizim tek basina birakilsaydi agac
          yapisi yardimci teknolojide tamamen kaybolurdu. */}
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
        <TextTree nodes={roots} />
      </Box>
    </Box>
  );
}

function TextTree({ nodes }: { nodes: OrgNode[] }) {
  return (
    <ul>
      {nodes.map((node) => (
        <li key={node.id}>
          {`${node.firstName} ${node.lastName}, ${node.jobTitle}, ${node.departmentName}`}
          {node.reports.length > 0 && <TextTree nodes={node.reports} />}
        </li>
      ))}
    </ul>
  );
}
