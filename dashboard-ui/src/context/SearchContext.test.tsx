/**
 * SearchContext Tests
 * Verifies search context state management
 */

import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SearchProvider, useSearch } from './SearchContext';

// Test component that uses the search context
function TestComponent() {
  const { query, setQuery, clearSearch } = useSearch();
  
  return (
    <div>
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search"
        data-testid="search-input"
      />
      <div data-testid="search-query">{query}</div>
      <button onClick={clearSearch} data-testid="clear-button">
        Clear
      </button>
    </div>
  );
}

describe('SearchContext', () => {
  it('provides search query state', () => {
    render(
      <SearchProvider>
        <TestComponent />
      </SearchProvider>
    );
    
    const searchInput = screen.getByTestId('search-input') as HTMLInputElement;
    expect(searchInput).toBeInTheDocument();
    expect(searchInput.value).toBe('');
  });

  it('initializes with empty query', () => {
    render(
      <SearchProvider>
        <TestComponent />
      </SearchProvider>
    );
    
    const queryDisplay = screen.getByTestId('search-query');
    expect(queryDisplay.textContent).toBe('');
  });

  it('updates query when setQuery is called', () => {
    render(
      <SearchProvider>
        <TestComponent />
      </SearchProvider>
    );
    
    const searchInput = screen.getByTestId('search-input') as HTMLInputElement;
    fireEvent.change(searchInput, { target: { value: 'test' } });
    
    const queryDisplay = screen.getByTestId('search-query');
    expect(queryDisplay.textContent).toBe('test');
  });

  it('clears query when clearSearch is called', () => {
    render(
      <SearchProvider>
        <TestComponent />
      </SearchProvider>
    );
    
    const searchInput = screen.getByTestId('search-input') as HTMLInputElement;
    
    // Set a query
    fireEvent.change(searchInput, { target: { value: 'test query' } });
    
    const queryDisplay = screen.getByTestId('search-query');
    expect(queryDisplay.textContent).toBe('test query');
    
    // Clear it
    const clearButton = screen.getByTestId('clear-button');
    fireEvent.click(clearButton);
    
    expect(queryDisplay.textContent).toBe('');
  });

  it('throws error when useSearch is used outside provider', () => {
    // Suppress console.error for this test since we expect an error
    const consoleError = console.error;
    console.error = () => {};
    
    expect(() => {
      render(<TestComponent />);
    }).toThrow('useSearch must be used within a SearchProvider');
    
    console.error = consoleError;
  });

  it('handles multiple state updates', () => {
    render(
      <SearchProvider>
        <TestComponent />
      </SearchProvider>
    );
    
    const searchInput = screen.getByTestId('search-input') as HTMLInputElement;
    const queryDisplay = screen.getByTestId('search-query');
    
    // First update
    fireEvent.change(searchInput, { target: { value: 'first' } });
    expect(queryDisplay.textContent).toBe('first');
    
    // Second update
    fireEvent.change(searchInput, { target: { value: 'second' } });
    expect(queryDisplay.textContent).toBe('second');
    
    // Clear
    const clearButton = screen.getByTestId('clear-button');
    fireEvent.click(clearButton);
    expect(queryDisplay.textContent).toBe('');
  });
});
