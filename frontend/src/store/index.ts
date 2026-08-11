import { combineReducers, configureStore, createAction } from '@reduxjs/toolkit';
import type { Action } from '@reduxjs/toolkit';
import { useDispatch, useSelector } from 'react-redux';
import employeesReducer from './employeesSlice';

/** Oturum bittiginde (cikis ya da 401) yayinlanir. */
export const sessionEnded = createAction('session/ended');

const combined = combineReducers({
  employees: employeesReducer,
});

/**
 * Oturum bitince TUM store sifirlanir.
 *
 * Aksi halde ayni tarayicida giris yapan ikinci kullanici, kendi verisi
 * gelene kadar oncekinin personel listesini gorur. Ortak kullanilan bir
 * bilgisayarda bu kozmetik degil, veri sizintisidir.
 *
 * Her slice'a ayri bir "temizle" eylemi yazmak yerine kok seviyede state'i
 * undefined yapiyoruz: her reducer kendi initialState'ine doner ve yeni bir
 * slice eklendiginde burasi degistirilmeden o da temizlenir.
 */
export function rootReducer(state: ReturnType<typeof combined> | undefined, action: Action) {
  if (action.type === sessionEnded.type) {
    return combined(undefined, action);
  }
  return combined(state, action);
}

export const store = configureStore({
  reducer: rootReducer,
});

// Store'un kendisinden turetilen tipler: reducer eklendiginde bu tipler
// kendiliginden genisler, elle guncellenmesi gerekmez.
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// Her bilesende tip yazmamak icin tipli sarmalayicilar.
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
