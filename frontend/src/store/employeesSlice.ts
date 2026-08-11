import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { employeeApi } from '../api/employees';
import { errorMessage } from '../api/client';
import type { Employee } from '../types/api';

interface EmployeesState {
  items: Employee[];
  totalElements: number;
  page: number;
  size: number;
  status: 'idle' | 'loading' | 'succeeded' | 'failed';
  error: string | null;
  /** Ust uste binen isteklerde yalnizca EN SON istegin cevabi kabul edilir. */
  currentRequestId: string | null;
  /** Pasiflestirme suren kaydin id'si; dugme bu sirada devre disi kalir. */
  deactivatingId: number | null;
}

const initialState: EmployeesState = {
  items: [],
  totalElements: 0,
  page: 0,
  size: 10,
  status: 'idle',
  error: null,
  currentRequestId: null,
  deactivatingId: null,
};

/**
 * Async thunk uc durum uretir: pending, fulfilled, rejected.
 *
 * "Yukleniyor" ve "hata" durumlarini elle yonetmek yerine bunlari yakalamak,
 * yukleme gostergesini unutma ihtimalini ortadan kaldirir.
 */
export const fetchEmployees = createAsyncThunk(
  'employees/fetch',
  async ({ page, size }: { page: number; size: number }, { rejectWithValue }) => {
    try {
      return await employeeApi.list(page, size);
    } catch (error) {
      return rejectWithValue(errorMessage(error));
    }
  },
);

export const deactivateEmployee = createAsyncThunk(
  'employees/deactivate',
  async (id: number, { rejectWithValue }) => {
    try {
      await employeeApi.deactivate(id);
      return id;
    } catch (error) {
      return rejectWithValue(errorMessage(error));
    }
  },
);

const employeesSlice = createSlice({
  name: 'employees',
  initialState,
  reducers: {
    pageChanged(state, action: { payload: number }) {
      state.page = action.payload;
    },
    pageSizeChanged(state, action: { payload: number }) {
      state.size = action.payload;
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
      .addCase(deactivateEmployee.pending, (state, action) => {
        state.deactivatingId = action.meta.arg;
      })
      .addCase(deactivateEmployee.fulfilled, (state, action) => {
        // Sunucu 204 donuyor, guncel satiri gondermiyor. Listeyi yeniden
        // cekmek yerine yerel kaydi isaretliyoruz: bir ag turu tasarruf.
        const employee = state.items.find((item) => item.id === action.payload);
        if (employee) {
          employee.active = false;
        }
        state.deactivatingId = null;
      })
      .addCase(deactivateEmployee.rejected, (state, action) => {
        state.error = action.payload as string;
        state.deactivatingId = null;
      });
  },
});

export const { pageChanged, pageSizeChanged, errorCleared } = employeesSlice.actions;
export default employeesSlice.reducer;
