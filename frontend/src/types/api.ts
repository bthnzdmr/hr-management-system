// Backend'in sozlesmesinin TypeScript karsiligi.
// Alan adlari birebir ayni olmalidir; Jackson bu adlarla serilestiriyor.

export type Role = 'ADMIN' | 'USER';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface Department {
  id: number;
  name: string;
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
  salary: string | null;
}

export interface SalaryUpdateRequest {
  salary: string;
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
