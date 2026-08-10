import { configureStore } from '@reduxjs/toolkit';
import { useDispatch, useSelector } from 'react-redux';
import employeesReducer from './employeesSlice';

export const store = configureStore({
  reducer: {
    employees: employeesReducer,
  },
});

// Store'un kendisinden turetilen tipler: reducer eklendiginde bu tipler
// kendiliginden genisler, elle guncellenmesi gerekmez.
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// Her bilesende tip yazmamak icin tipli sarmalayicilar.
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
