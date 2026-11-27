import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { staffLanguage, staffStrings, setStaffLanguage } from './staff';
import Login from '@/pages/Login';
import Sidebar from '@/components/Sidebar';
import OrderStatusBadge from '@/components/OrderStatusBadge';
import OrderQrPanel from '@/components/OrderQrPanel';

const LANGUAGE_KEY = 'readybell-staff-language';

describe('staff strings', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('has every key in both languages and no empty text', () => {
    const en = staffStrings('en');
    const uk = staffStrings('uk');
    expect(Object.keys(uk).sort()).toEqual(Object.keys(en).sort());
    for (const [key, value] of Object.entries(uk)) {
      if (typeof value === 'string') expect(value.trim(), key).not.toBe('');
    }
  });

  it('formats the page counter in both languages', () => {
    expect(staffStrings('en').pageOf(2, 5, 48)).toBe('Page 2 of 5 (48 total orders)');
    expect(staffStrings('uk').pageOf(2, 5, 48)).toBe('Сторінка 2 з 5 (усього замовлень: 48)');
  });

  it('follows the browser language when nothing was chosen', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('uk-UA');
    expect(staffLanguage()).toBe('uk');
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('de-DE');
    expect(staffLanguage()).toBe('en');
  });

  it('prefers the language staff picked over the browser', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('uk-UA');
    localStorage.setItem(LANGUAGE_KEY, 'en');
    expect(staffLanguage()).toBe('en');
  });

  it('remembers the choice and reloads the page', () => {
    const reload = vi.fn();
    Object.defineProperty(window, 'location', { value: { ...window.location, reload }, writable: true });

    setStaffLanguage('uk');

    expect(localStorage.getItem(LANGUAGE_KEY)).toBe('uk');
    expect(reload).toHaveBeenCalled();
  });
});

describe('screens in Ukrainian', () => {
  beforeEach(() => localStorage.setItem(LANGUAGE_KEY, 'uk'));
  afterEach(() => localStorage.clear());

  it('shows the sign-in form in Ukrainian', () => {
    render(<Login />);
    expect(screen.getByLabelText('Ресторан')).toBeInTheDocument();
    expect(screen.getByLabelText('Ім’я користувача')).toBeInTheDocument();
    expect(screen.getByLabelText('Пароль')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Увійти' })).toBeInTheDocument();
  });

  it('shows the navigation, the panel name and the way back to English', () => {
    render(<MemoryRouter><Sidebar role="RESTAURANT_ADMIN" /></MemoryRouter>);
    expect(screen.getAllByText('Панель').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Персонал').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Налаштування').length).toBeGreaterThan(0);
    expect(screen.getByText('Панель ресторану')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Перемкнути на англійську' })).toBeInTheDocument();
  });

  it('names the order statuses in Ukrainian', () => {
    render(<OrderStatusBadge status="PREPARING" />);
    expect(screen.getByText('Готується')).toBeInTheDocument();
  });

  it('shows the QR hand-over panel in Ukrainian', () => {
    render(<OrderQrPanel orderNumber="#7" url="https://x/track/1" qr="data:image/png;base64,AA" restaurantName="Кафе" connected />);
    expect(screen.getByText('Клієнта підключено')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Показати QR-код знову' })).toBeInTheDocument();
  });
});
