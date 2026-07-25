import { createContext, useContext } from "react";
import type React from "react";

export type ThemeMode = "light" | "dark";
export type AppLocale = "en" | "ko";
export type AppPreferences = {
  theme: ThemeMode;
  setTheme: React.Dispatch<React.SetStateAction<ThemeMode>>;
  locale: AppLocale;
  setLocale: React.Dispatch<React.SetStateAction<AppLocale>>;
};

export const AppPreferencesContext = createContext<AppPreferences | null>(null);
export const THEME_STORAGE_KEY = "meetingmind-theme";
export const LOCALE_STORAGE_KEY = "meetingmind-locale";

export function storedTheme(): ThemeMode {
  return window.localStorage.getItem(THEME_STORAGE_KEY) === "dark" ? "dark" : "light";
}

export function storedLocale(): AppLocale {
  return window.localStorage.getItem(LOCALE_STORAGE_KEY) === "ko" ? "ko" : "en";
}

export function useAppPreferences() {
  const context = useContext(AppPreferencesContext);
  if (!context) {
    throw new Error("AppPreferencesContext is not available.");
  }
  return context;
}
