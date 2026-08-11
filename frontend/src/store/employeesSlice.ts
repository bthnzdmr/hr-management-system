import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import { employeeApi } from '../api/employees';
import { errorMessage } from '../api/client';
import type { Employee } from '../types/api';

/** Sunucunun siralayabilecegi alanlar. Entity ozellik adlariyla birebir aynidir. */
export type SortField = 'lastName' | 'firstName' | 'email' | 'jobTitle' | 'hireDate';
export type SortDirection = 'asc' | 'desc';
export type ActiveFilter = 'all' | 'active' | 'inactive';

interface EmployeesState {
  items: Employee[];
  totalElements: number;
  page: number;
  size: number;
  search: string;
  activeFilter: ActiveFilter;
  sortField: SortField;
  sortDirection: SortDirection;
  status: 'idle' | 'loading' | 'succeeded' | 'failed';
  error: string | null;
  /** Ust uste binen isteklerde yalnizca EN SON istegin cevabi kabul edilir. */
  currentRequestId: string | null;
  /** Durumu degistirilen kaydin id'si; dugme bu sirada devre disi kalir. */
  statusChangingId: number | null;
}

const initialState: EmployeesState = {
  items: [],
  totalElements: 0,
  page: 0,
  size: 10,
  search: '',
  activeFilter: 'all',
  sortField: 'lastName',
  sortDirection: 'asc',
  status: 'idle',
  error: null,
  currentRequestId: null,
  statusChangingId: null,
};

/** 'all' filtresinde parametre HIC gonderilmez; false gondermek "pasifler" demektir. */
function toActiveParam(filter: ActiveFilter): boolean | undefined {
  if (filter === 'active') return true;
  if (filter === 'inactive') return false;
  return undefined;
}

interface FetchArgs {
  page: number;
  size: number;
  search: string;
  activeFilter: ActiveFilter;
  sortField: SortField;
  sortDirection: SortDirection;
}

/**
 * Async thunk uc durum uretir: pending, fulfilled, rejected.
 *
 * "Yukleniyor" ve "hata" durumlarini elle yonetmek yerine bunlari yakalamak,
 * yukleme gostergesini unutma ihtimalini ortadan kaldirir.
 */
export const fetchEmployees = createAsyncThunk(
  'employees/fetch',
  async (args: FetchArgs, { rejectWithValue }) => {
    try {
      return await employeeApi.list({
        page: args.page,
        size: args.size,
        sort: `${args.sortField},${args.sortDirection}`,
        search: args.search.trim() || undefined,
        active: toActiveParam(args.activeFilter),
      });
    } catch (error) {
      return rejectWithValue(errorMessage(error));
    }
  },
);

export const changeEmployeeStatus = createAsyncThunk(
  'employees/changeStatus',
  async ({ id, active }: { id: number; active: boolean }, { rejectWithValue }) => {
    try {
      return await employeeApi.changeStatus(id, active);
    } catch (error) {
      return rejectWithValue(errorMessage(error));
    }
  },
);

const employeesSlice = createSlice({
  name: 'employees',
  initialState,
  reducers: {
    pageChanged(state, action: PayloadAction<number>) {
      state.page = action.payload;
    },
    pageSizeChanged(state, action: PayloadAction<number>) {
      state.size = action.payload;
      state.page = 0;
    },
    // Arama ve filtre daima ilk sayfaya doner: uc numarali sayfadayken
    // daraltilan sonuc tek sayfaya sigar ve kullanici bos ekran gorurdu.
    searchChanged(state, action: PayloadAction<string>) {
      state.search = action.payload;
      state.page = 0;
    },
    activeFilterChanged(state, action: PayloadAction<ActiveFilter>) {
      state.activeFilter = action.payload;
      state.page = 0;
    },
    sortChanged(state, action: PayloadAction<SortField>) {
      // Ayni sutuna tekrar tiklamak yonu cevirir, farkli sutun artan baslar.
      if (state.sortField === action.payload) {
        state.sortDirection = state.sortDirection === 'asc' ? 'desc' : 'asc';
      } else {
        state.sortField = action.payload;
        state.sortDirection = 'asc';
      }
      state.page = 0;
    },
    errorCleared(state) {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchEmployees.pending, (state, action) => {
        state.status = 'loading';
        state.error = null;
        state.currentRequestId = action.meta.requestId;
      })
      .addCase(fetchEmployees.fulfilled, (state, action) => {
        // Hizli sayfa degisiminde istekler ust uste biner. Gec donen ESKI
        // cevabin yeni sayfanin uzerine yazmasini engelliyoruz.
        if (state.currentRequestId !== action.meta.requestId) {
          return;
        }
        state.status = 'succeeded';
        state.items = action.payload.content;
        state.totalElements = action.payload.totalElements;
        state.currentRequestId = null;
      })
      .addCase(fetchEmployees.rejected, (state, action) => {
        if (state.currentRequestId !== action.meta.requestId) {
          return;
        }
        state.status = 'failed';
        state.error = action.payload as string;
        // Eski sayfanin satirlari temizlenir: hata bandi ustte dururken
        // altta baska bir sayfanin verisini gostermek yaniltici olur.
        state.items = [];
        state.totalElements = 0;
        state.currentRequestId = null;
      })
      .addCase(changeEmployeeStatus.pending, (state, action) => {
        state.statusChangingId = action.meta.arg.id;
      })
      .addCase(changeEmployeeStatus.fulfilled, (state, action) => {
        // Sunucu guncel kaydi doner; listeyi bastan cekmek yerine yalnizca
        // degisen satir degistirilir. Bir ag turu tasarruf.
        const index = state.items.findIndex((item) => item.id === action.payload.id);
        if (index !== -1) {
          state.items[index] = action.payload;
        }
        state.statusChangingId = null;
      })
      .addCase(changeEmployeeStatus.rejected, (state, action) => {
        state.error = action.payload as string;
        state.statusChangingId = null;
      });
  },
});

export const {
  pageChanged,
  pageSizeChanged,
  searchChanged,
  activeFilterChanged,
  sortChanged,
  errorCleared,
} = employeesSlice.actions;
export default employeesSlice.reducer;
