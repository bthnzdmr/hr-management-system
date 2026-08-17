import { ROLE_LABELS, TERMINATION_REASON_LABELS } from '../types/api';
import type { Role, TerminationReason } from '../types/api';
import { formatDay } from '../utils/formatDate';

/**
 * Denetim kaydinin `detail` alanini insan cumlesine cevirir.
 *
 * Sunucu detayi MEKANIK uretiyor: `AuditAspect` metot argumanlarini
 * "ad=deger" diye yaziyor ve ekranda "active=true" gibi gorunuyordu.
 *
 * Cevirme SUNUCUDA degil BURADA yapiliyor, iki sebeple:
 *
 * 1. Denetim kaydi bir OLGUDUR. Ne yazildigini sunuma gore sekillendirmek,
 *    izin degerini dusurur; depoda ham olan, ekranda okunur olabilir.
 * 2. Okuma aninda bicimlendirmek ZATEN YAZILMIS satirlari da duzeltir.
 *    Sunucuda degistirseydik tablo eski ve yeni bicimin karisimi kalirdi ve
 *    eski satirlar sonsuza kadar "active=true" gorunurdu.
 *
 * Taninmayan her sey HAM haliyle gecer: okunmasi zor bir satir birakmak,
 * bilgi kaybetmekten iyidir.
 */

/** Alan adlarinin insan karsiligi. */
const FIELD_LABELS: Record<string, string> = {
  email: 'Email',
  name: 'Name',
  firstName: 'First name',
  lastName: 'Last name',
  jobTitle: 'Job title',
  departmentId: 'Department',
  managerId: 'Manager',
  employeeId: 'Employee',
  hireDate: 'Hired',
  startDate: 'From',
  endDate: 'To',
  note: 'Note',
  phone: 'Phone',
};

/** Tarih gibi okunmasi gereken alanlar. */
const DATE_FIELDS = new Set(['hireDate', 'startDate', 'endDate']);

/**
 * Virgulle ayrilmis alanlari boler, ama KOSELI PARANTEZ ICINDEKILERI bolmez.
 *
 * Duz bir `split(', ')` "roles=[EMPLOYEE, MANAGER]" ifadesini ikiye kesip
 * "MANAGER]" diye bir alan uretirdi.
 */
function splitFields(text: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = '';

  for (const char of text) {
    if (char === '[') depth += 1;
    if (char === ']') depth -= 1;

    if (char === ',' && depth === 0) {
      parts.push(current.trim());
      current = '';
    } else {
      current += char;
    }
  }

  if (current.trim()) parts.push(current.trim());

  return parts;
}

/** "[A, B]" -> ["A", "B"]; kose parantez yoksa tek elemanli liste. */
function items(value: string): string[] {
  const inner = value.startsWith('[') && value.endsWith(']')
    ? value.slice(1, -1)
    : value;

  return inner.split(',').map((item) => item.trim()).filter(Boolean);
}

function roleNames(value: string): string {
  return items(value)
    .map((role) => ROLE_LABELS[role as Role] ?? role)
    .join(', ');
}

function reasonName(value: string): string {
  return TERMINATION_REASON_LABELS[value as TerminationReason] ?? value;
}

/** "Type[a=1, b=2]" ise ic alanlari doner; degilse null. */
function unwrapRecord(value: string): string | null {
  const open = value.indexOf('[');

  if (open <= 0 || !value.endsWith(']')) return null;

  return value.slice(open + 1, -1);
}

function humanise(key: string, value: string): string | null {
  if (key === 'active') return value === 'true' ? 'Activated' : 'Deactivated';
  if (key === 'reason') return reasonName(value);
  if (key === 'roles') return `Roles: ${roleNames(value)}`;

  // Sir tasiyan alanlar DTO'nun toString'inde zaten maskeleniyor; maskeli
  // degeri ekrana tasimanin bir faydasi yok.
  if (value === '***') return null;

  const label = FIELD_LABELS[key];
  const shown = DATE_FIELDS.has(key) ? formatDay(value) : value;

  return label ? `${label}: ${shown}` : `${key}: ${shown}`;
}

/** Tek bir "ad=deger" parcasini cozer. */
function describeField(field: string): string | null {
  const at = field.indexOf('=');

  // Esittik yoksa bu bir OZET kelimesidir ("approved", "rejected").
  if (at < 0) return field.charAt(0).toUpperCase() + field.slice(1);

  const key = field.slice(0, at);
  const value = field.slice(at + 1);

  // "request=UserCreateRequest[...]" gibi sarmalanmis bir DTO: disini atip
  // icindeki alanlari goster. Sarmalayicinin adi ekranda bir sey anlatmiyor.
  const inner = unwrapRecord(value);
  if (inner !== null) return describeDetail(inner);

  return humanise(key, value);
}

/**
 * Denetim detayini okunur hale getirir.
 *
 * Bos veya cozulemeyen girdi icin null doner; cagiran taraf "—" gosterir.
 */
export function describeDetail(detail: string | null): string | null {
  if (!detail || !detail.trim()) return null;

  const parts = splitFields(detail)
    .map(describeField)
    .filter((part): part is string => part !== null && part.length > 0);

  return parts.length > 0 ? parts.join(' · ') : null;
}
