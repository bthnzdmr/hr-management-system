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
}

const initialState: EmployeesState = {
  items: [],
  totalElements: 0,
  page: 0,
  size: 10,
  status: 'idle',
  error: null,
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
      .addCase(fetchEmployees.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(fetchEmployees.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.items = action.payload.content;
        state.totalElements = action.payload.totalElements;
      })
      .addCase(fetchEmployees.rejected, (state, action) => {
        state.status = 'failed';
        state.error = action.payload as string;
      })
      .addCase(deactivateEmployee.fulfilled, (state, action) => {
        // Sunucu 204 donuyor, guncel satiri gondermiyor. Listeyi yeniden
        // cekmek yerine yerel kaydi isaretliyoruz: bir ag turu tasarruf.
        const employee = state.items.find((item) => item.id === action.payload);
        if (employee) {
          employee.active = false;
        }
      })
      .addCase(deactivateEmployee.rejected, (state, action) => {
        state.error = action.payload as string;
      });
  },
});

export const { pageChanged, pageSizeChanged, errorCleared } = employeesSlice.actions;
export default employeesSlice.reducer;
