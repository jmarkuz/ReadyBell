/**
 * Status Filter Component Tests
 * Verifies StatusFilter renders with checkboxes, select all/clear all, apply/cancel buttons,
 * and badge showing count of active filters
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@/test/test-utils';
import StatusFilter from './StatusFilter';

describe('StatusFilter Component', () => {
  // ============================================================================
  // RENDERING TESTS
  // ============================================================================
  
  it('renders status filter button without errors', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    expect(button).toBeInTheDocument();
  });

  it('renders filter button with "Status" label', () => {
    render(<StatusFilter />);
    expect(screen.getByText('Status')).toBeInTheDocument();
  });

  it('renders ChevronDown icon in button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    expect(button.querySelector('svg')).toBeInTheDocument();
  });

  // ============================================================================
  // DROPDOWN VISIBILITY TESTS
  // ============================================================================

  it('opens dropdown when button is clicked', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    expect(screen.getByText('Filter by Status')).toBeInTheDocument();
  });

  it('closes dropdown when button is clicked again', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    
    // Open
    fireEvent.click(button);
    expect(screen.getByText('Filter by Status')).toBeInTheDocument();
    
    // Close
    fireEvent.click(button);
    expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
  });

  it('closes dropdown when close button is clicked', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const closeButton = screen.getByLabelText('Close filter dropdown');
    fireEvent.click(closeButton);
    
    expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
  });

  it('closes dropdown when clicking on the overlay', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    expect(screen.getByText('Filter by Status')).toBeInTheDocument();
    
    // Find the overlay div (it's a fixed element that covers the screen)
    // We can query for it by finding all divs and locating the one with aria-hidden="true" and fixed positioning
    const overlayDiv = document.querySelector('div[aria-hidden="true"]');
    expect(overlayDiv).toBeInTheDocument();
    
    if (overlayDiv) {
      fireEvent.click(overlayDiv);
    }
    
    expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
  });

  // ============================================================================
  // CHECKBOX TESTS
  // ============================================================================

  it('renders all four status checkboxes when dropdown is open', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    expect(screen.getByLabelText('Filter by New Order')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter by Preparing')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter by Ready for Pickup')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter by Completed')).toBeInTheDocument();
  });

  it('renders correct status labels', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    expect(screen.getByText('New Order')).toBeInTheDocument();
    expect(screen.getByText('Preparing')).toBeInTheDocument();
    expect(screen.getByText('Ready for Pickup')).toBeInTheDocument();
    expect(screen.getByText('Completed')).toBeInTheDocument();
  });

  it('toggles checkbox when clicked', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const createdCheckbox = screen.getByLabelText('Filter by New Order') as HTMLInputElement;
    expect(createdCheckbox.checked).toBe(false);
    
    fireEvent.click(createdCheckbox);
    expect(createdCheckbox.checked).toBe(true);
    
    fireEvent.click(createdCheckbox);
    expect(createdCheckbox.checked).toBe(false);
  });

  it('supports checking multiple statuses', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const createdCheckbox = screen.getByLabelText('Filter by New Order') as HTMLInputElement;
    const preparingCheckbox = screen.getByLabelText('Filter by Preparing') as HTMLInputElement;
    const readyCheckbox = screen.getByLabelText('Filter by Ready for Pickup') as HTMLInputElement;
    
    fireEvent.click(createdCheckbox);
    fireEvent.click(preparingCheckbox);
    fireEvent.click(readyCheckbox);
    
    expect(createdCheckbox.checked).toBe(true);
    expect(preparingCheckbox.checked).toBe(true);
    expect(readyCheckbox.checked).toBe(true);
  });

  // ============================================================================
  // SELECT ALL / CLEAR ALL TESTS
  // ============================================================================

  it('renders Select All button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const selectAllButton = screen.getByRole('button', { name: 'Select all statuses' });
    expect(selectAllButton).toBeInTheDocument();
  });

  it('renders Clear All button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const clearAllButton = screen.getByRole('button', { name: 'Clear all statuses' });
    expect(clearAllButton).toBeInTheDocument();
  });

  it('selects all statuses when Select All is clicked', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const selectAllButton = screen.getByRole('button', { name: 'Select all statuses' });
    fireEvent.click(selectAllButton);
    
    expect(screen.getByLabelText('Filter by New Order') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Preparing') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Ready for Pickup') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Completed') as HTMLInputElement).toHaveProperty('checked', true);
  });

  it('clears all statuses when Clear All is clicked', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    // Select all first
    const selectAllButton = screen.getByRole('button', { name: 'Select all statuses' });
    fireEvent.click(selectAllButton);
    
    // Then clear all
    const clearAllButton = screen.getByRole('button', { name: 'Clear all statuses' });
    fireEvent.click(clearAllButton);
    
    expect(screen.getByLabelText('Filter by New Order') as HTMLInputElement).toHaveProperty('checked', false);
    expect(screen.getByLabelText('Filter by Preparing') as HTMLInputElement).toHaveProperty('checked', false);
    expect(screen.getByLabelText('Filter by Ready for Pickup') as HTMLInputElement).toHaveProperty('checked', false);
    expect(screen.getByLabelText('Filter by Completed') as HTMLInputElement).toHaveProperty('checked', false);
  });

  it('disables Select All button when all statuses are selected', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const selectAllButton = screen.getByRole('button', { name: 'Select all statuses' });
    fireEvent.click(selectAllButton);
    
    expect(selectAllButton).toBeDisabled();
  });

  it('disables Clear All button when no statuses are selected', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const clearAllButton = screen.getByRole('button', { name: 'Clear all statuses' });
    // Should already be disabled since nothing is selected by default
    expect(clearAllButton).toBeDisabled();
  });

  // ============================================================================
  // APPLY / CANCEL BUTTON TESTS
  // ============================================================================

  it('renders Apply button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    expect(applyButton).toBeInTheDocument();
  });

  it('renders Cancel button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const cancelButton = screen.getByRole('button', { name: 'Cancel' });
    expect(cancelButton).toBeInTheDocument();
  });

  it('fires onApply callback when Apply is clicked with selected statuses', () => {
    const onApply = vi.fn();
    render(<StatusFilter onApply={onApply} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    // Select a status
    const createdCheckbox = screen.getByLabelText('Filter by New Order');
    fireEvent.click(createdCheckbox);
    
    // Click Apply
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    fireEvent.click(applyButton);
    
    expect(onApply).toHaveBeenCalledWith(['CREATED']);
  });

  it('fires onApply with multiple statuses', () => {
    const onApply = vi.fn();
    render(<StatusFilter onApply={onApply} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    // Select multiple statuses
    fireEvent.click(screen.getByLabelText('Filter by New Order'));
    fireEvent.click(screen.getByLabelText('Filter by Preparing'));
    fireEvent.click(screen.getByLabelText('Filter by Ready for Pickup'));
    
    // Click Apply
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    fireEvent.click(applyButton);
    
    expect(onApply).toHaveBeenCalledWith(['CREATED', 'PREPARING', 'READY']);
  });

  it('fires onCancel callback when Cancel is clicked', () => {
    const onCancel = vi.fn();
    render(<StatusFilter onCancel={onCancel} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const cancelButton = screen.getByRole('button', { name: 'Cancel' });
    fireEvent.click(cancelButton);
    
    expect(onCancel).toHaveBeenCalled();
  });

  it('closes dropdown when Apply is clicked', () => {
    render(<StatusFilter onApply={vi.fn()} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    expect(screen.getByText('Filter by Status')).toBeInTheDocument();
    
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    fireEvent.click(applyButton);
    
    expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
  });

  it('closes dropdown when Cancel is clicked', () => {
    render(<StatusFilter onCancel={vi.fn()} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    expect(screen.getByText('Filter by Status')).toBeInTheDocument();
    
    const cancelButton = screen.getByRole('button', { name: 'Cancel' });
    fireEvent.click(cancelButton);
    
    expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
  });

  it('resets checkboxes to initial state when Cancel is clicked', () => {
    render(<StatusFilter selectedStatuses={['CREATED', 'PREPARING']} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    // Checkboxes should reflect initial state
    expect(screen.getByLabelText('Filter by New Order') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Preparing') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Ready for Pickup') as HTMLInputElement).toHaveProperty('checked', false);
    
    // Change selection
    fireEvent.click(screen.getByLabelText('Filter by New Order'));
    fireEvent.click(screen.getByLabelText('Filter by Ready for Pickup'));
    
    // Cancel should revert to initial state
    const cancelButton = screen.getByRole('button', { name: 'Cancel' });
    fireEvent.click(cancelButton);
    
    // Reopen dropdown
    fireEvent.click(button);
    
    // Should be back to initial state
    expect(screen.getByLabelText('Filter by New Order') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Preparing') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Ready for Pickup') as HTMLInputElement).toHaveProperty('checked', false);
  });

  // ============================================================================
  // BADGE TESTS
  // ============================================================================

  it('does not show badge when no statuses are selected', () => {
    render(<StatusFilter selectedStatuses={[]} />);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('shows badge with count when statuses are selected', () => {
    render(<StatusFilter selectedStatuses={['CREATED']} />);
    const badge = screen.getByText('1');
    expect(badge).toBeInTheDocument();
  });

  it('shows badge with correct count for multiple statuses', () => {
    render(<StatusFilter selectedStatuses={['CREATED', 'PREPARING', 'READY']} />);
    const badge = screen.getByText('3');
    expect(badge).toBeInTheDocument();
  });

  it('displays all 4 in badge when all statuses selected', () => {
    render(
      <StatusFilter selectedStatuses={['CREATED', 'PREPARING', 'READY', 'COMPLETED']} />
    );
    const badge = screen.getByText('4');
    expect(badge).toBeInTheDocument();
  });

  // ============================================================================
  // ACCESSIBILITY TESTS
  // ============================================================================

  it('has proper aria-label on filter button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    expect(button).toHaveAttribute('aria-label');
  });

  it('updates aria-label when statuses are selected', () => {
    render(<StatusFilter selectedStatuses={['CREATED', 'PREPARING']} />);
    const button = screen.getByRole('button', { name: /status filter.*2 selected/i });
    expect(button).toBeInTheDocument();
  });

  it('has aria-expanded attribute on button', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    expect(button).toHaveAttribute('aria-expanded');
  });

  it('updates aria-expanded when dropdown opens/closes', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    
    expect(button).toHaveAttribute('aria-expanded', 'false');
    
    fireEvent.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
  });

  it('all checkboxes are keyboard accessible', () => {
    render(<StatusFilter />);
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes.length).toBe(4);
    checkboxes.forEach((checkbox) => {
      expect(checkbox).toHaveAttribute('aria-label');
    });
  });

  // ============================================================================
  // INTEGRATION TESTS
  // ============================================================================

  it('handles complete workflow: open, select, apply', () => {
    const onApply = vi.fn();
    render(<StatusFilter onApply={onApply} />);
    
    // Open dropdown
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    // Select statuses
    fireEvent.click(screen.getByLabelText('Filter by New Order'));
    fireEvent.click(screen.getByLabelText('Filter by Preparing'));
    
    // Apply
    const applyButton = screen.getByRole('button', { name: 'Apply' });
    fireEvent.click(applyButton);
    
    expect(onApply).toHaveBeenCalledWith(['CREATED', 'PREPARING']);
    expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
  });

  it('handles complete workflow: open, select all, clear, apply', () => {
    const onApply = vi.fn();
    render(<StatusFilter onApply={onApply} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    fireEvent.click(button);
    
    // Select all
    fireEvent.click(screen.getByRole('button', { name: 'Select all statuses' }));
    
    // Clear all
    fireEvent.click(screen.getByRole('button', { name: 'Clear all statuses' }));
    
    // Apply
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    
    expect(onApply).toHaveBeenCalledWith([]);
  });

  it('preserves selections between dropdown open/close/reopen', () => {
    const onApply = vi.fn();
    render(<StatusFilter onApply={onApply} />);
    
    const button = screen.getByRole('button', { name: /status filter/i });
    
    // Open and select
    fireEvent.click(button);
    fireEvent.click(screen.getByLabelText('Filter by New Order'));
    fireEvent.click(screen.getByLabelText('Filter by Preparing'));
    
    // Close (without applying)
    fireEvent.click(button);
    
    // Reopen
    fireEvent.click(button);
    
    // Selections should still be there
    expect(screen.getByLabelText('Filter by New Order') as HTMLInputElement).toHaveProperty('checked', true);
    expect(screen.getByLabelText('Filter by Preparing') as HTMLInputElement).toHaveProperty('checked', true);
    
    // Apply now
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(onApply).toHaveBeenCalledWith(['CREATED', 'PREPARING']);
  });
});
