// Backend'in sozlesmesinin TypeScript karsiligi.
// Alan adlari birebir ayni olmalidir; Jackson bu adlarla serilestiriyor.

/**
 * Roller IS ISLEVINE gore adlandirilir.
 *
 * SERVICE bir makine kimligidir (Notification Service); arayuzde secilebilir
 * olmasi gerekmez ama sunucudan gelebilir, bu yuzden tipte yer alir.
 */
export type Role = 'EMPLOYEE' | 'MANAGER' | 'HR_SPECIALIST' | 'SYSTEM_ADMIN' | 'SERVICE';

/** Insan tarafindan secilebilen roller; SERVICE listede yer almaz. */
export const ASSIGNABLE_ROLES: Role[] = [
  'EMPLOYEE',
  'MANAGER',
  'HR_SPECIALIST',
  'SYSTEM_ADMIN',
];

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  MANAGER: 'Manager',
  HR_SPECIALIST: 'HR specialist',
  SYSTEM_ADMIN: 'System administrator',
  SERVICE: 'Service account',
};

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  EMPLOYEE: 'Sees only their own record',
  MANAGER: 'Sees their own record and their direct reports',
  HR_SPECIALIST: 'Sees and edits everyone, including salaries',
  SYSTEM_ADMIN: 'Manages accounts and access; cannot edit HR data or see salaries',
  SERVICE: 'Machine identity used by the notification service',
};

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  // Uzun omurlu ama sunucuda kayitli: iptal edilebilir.
  refreshToken: string;
}

export interface Department {
  id: number;
  name: string;
}

/** Hesap. Parola ozeti sunucudan HIC gelmez. */
export interface User {
  id: number;
  email: string;
  roles: Role[];
  active: boolean;
  employeeId: number | null;
  employeeFullName: string | null;
  createdAt: string;
}

export interface UserCreateRequest {
  email: string;
  password: string;
  roles: Role[];
  employeeId: number | null;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface Employee {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  departmentId: number;
  departmentName: string;
  managerId: number | null;
  // Sunucu adi da gonderiyor; aksi halde arayuz her satir icin yoneticiyi
  // ayrica sorgulamak zorunda kalirdi (liste boyunca N+1 istek).
  managerFullName: string | null;
  jobTitle: string;
  hireDate: string;
  active: boolean;
}

export interface EmployeeCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  departmentId: number;
  managerId: number | null;
  jobTitle: string;
  hireDate: string;
  salary: string | null;
}

/**
 * Maas BILEREK yok.
 *
 * Genel guncelleme maasi tasisaydi, onu okuyamayan bu arayuz her kayitta null
 * gonderip silerdi -- gercekten yasanan bir hataydi. Maas kendi ucuyle yonetilir.
 */
export type EmployeeUpdateRequest = Omit<EmployeeCreateRequest, 'salary'>;

export interface SalaryResponse {
  employeeId: number;
  // Backend BigDecimal donuyor ve Jackson bunu JSON SAYISI olarak yaziyor.
  // Burada string demek, "degisti mi" karsilastirmasini sessizce bozar:
  // "95000" !== 95000 oldugu icin degismemis maas guncellenmis sayilirdi.
  salary: number | null;
}

export interface SalaryUpdateRequest {
  salary: number;
}

/** Spring Data'nin Page cevabinin kullandigimiz alanlari. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** RFC 7807 ProblemDetail. Backend her hatayi bu bicimde donuyor. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  errors?: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
}
