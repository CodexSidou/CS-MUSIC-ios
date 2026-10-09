export const darkTheme = {
  mode: 'dark',
  bg: '#0B0D12',
  bgSecondary: '#141821',
  surface: '#181D29',
  surfaceLight: '#202737',
  border: '#242B3B',
  borderLight: '#323C50',
  text: '#F5F7FA',
  textSecondary: '#929AA8',
  textMuted: '#646D7F',
  accentBlue: '#3285FF',
  accentPurple: '#8B5CF6',
  danger: '#EF4444',
  success: '#10B981',
  warning: '#F59E0B',
  miniPlayerBg: '#141821',
  tabBarBg: '#0B0D12',
  gradients: {
    primary: ['#3285FF', '#8B5CF6'],
    blue: ['#3285FF', '#1D4ED8'],
    card: ['#181D29', '#141821'],
    darkOverlay: ['rgba(11,13,18,0.2)', 'rgba(11,13,18,0.92)', '#0B0D12'],
  },
};

export const lightTheme = {
  mode: 'light',
  bg: '#F8FAFC',
  bgSecondary: '#F1F5F9',
  surface: '#FFFFFF',
  surfaceLight: '#F1F5F9',
  border: '#E2E8F0',
  borderLight: '#CBD5E1',
  text: '#0F172A',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  accentBlue: '#2563EB',
  accentPurple: '#7C3AED',
  danger: '#EF4444',
  success: '#10B981',
  warning: '#F59E0B',
  miniPlayerBg: '#FFFFFF',
  tabBarBg: '#F8FAFC',
  gradients: {
    primary: ['#2563EB', '#7C3AED'],
    blue: ['#2563EB', '#1D4ED8'],
    card: ['#FFFFFF', '#F8FAFC'],
    darkOverlay: ['rgba(248,250,252,0.2)', 'rgba(248,250,252,0.95)', '#F8FAFC'],
  },
};

// Default export uses darkTheme as primary
export const theme = darkTheme;