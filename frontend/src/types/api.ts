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

export type EmployeeUpdateRequest = EmployeeCreateRequest;

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
