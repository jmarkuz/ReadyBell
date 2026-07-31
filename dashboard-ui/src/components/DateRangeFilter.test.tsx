import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DateRangeFilter } from './DateRangeFilter';

describe('DateRangeFilter Component', () => {
  let mockOnApply: ReturnType<typeof vi.fn>;
  let mockOnClear: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockOnApply = vi.fn();
    mockOnClear = vi.fn();
  });

  describe('Rendering', () => {
    it('should render the component when isOpen is true', () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      expect(screen.getByText('Filter by Date Range')).toBeInTheDocument();
      expect(screen.getByText('Today')).toBeInTheDocument();
      expect(screen.getByText('This Week')).toBeInTheDocument();
      expect(screen.getByText('This Month')).toBeInTheDocument();
    });

    it('should not render when isOpen is false', () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={false}
        />
      );

      expect(screen.queryByText('Filter by Date Range')).not.toBeInTheDocument();
    });

    it('should render date input fields', () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      expect(screen.getByLabelText('Start Date')).toBeInTheDocument();
      expect(screen.getByLabelText('End Date')).toBeInTheDocument();
    });

    it('should render Apply and Clear buttons', () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      expect(screen.getByRole('button', { name: /Apply/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Clear/ })).toBeInTheDocument();
    });
  });

  describe('Preset Selection', () => {
    it('should select "Today" preset and update dates', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const todayButton = screen.getByRole('button', { name: 'Today' });
      await userEvent.click(todayButton);

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      const endDateInput = screen.getByLabelText('End Date') as HTMLInputElement;

      expect(startDateInput.value).toBe(endDateInput.value);
      expect(todayButton).toHaveAttribute('aria-pressed', 'true');
    });

    it('should select "This Week" preset and set appropriate date range', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const weekButton = screen.getByRole('button', { name: 'This Week' });
      await userEvent.click(weekButton);

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      const endDateInput = screen.getByLabelText('End Date') as HTMLInputElement;

      // Week button should be pressed
      expect(weekButton).toHaveAttribute('aria-pressed', 'true');

      // Start date should be before or equal to end date
      const startTime = new Date(startDateInput.value).getTime();
      const endTime = new Date(endDateInput.value).getTime();
      expect(startTime).toBeLessThanOrEqual(endTime);
    });

    it('should select "This Month" preset and set appropriate date range', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const monthButton = screen.getByRole('button', { name: 'This Month' });
      await userEvent.click(monthButton);

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      const endDateInput = screen.getByLabelText('End Date') as HTMLInputElement;

      // Month button should be pressed
      expect(monthButton).toHaveAttribute('aria-pressed', 'true');

      // Start date should be in the same month as end date
      const startDate = new Date(startDateInput.value);
      const endDate = new Date(endDateInput.value);
      expect(startDate.getMonth()).toBe(endDate.getMonth());
    });

    it('should deselect preset when manually entering dates', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const todayButton = screen.getByRole('button', { name: 'Today' });
      await userEvent.click(todayButton);
      expect(todayButton).toHaveAttribute('aria-pressed', 'true');

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      await userEvent.clear(startDateInput);
      await userEvent.type(startDateInput, '2024-01-15');

      // Preset should be deselected
      expect(todayButton).toHaveAttribute('aria-pressed', 'false');
    });
  });

  describe('Date Input Validation', () => {
    it('should not show error when start date <= end date', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-01');
      await userEvent.type(endDateInput, '2024-01-31');

      expect(screen.queryByText(/Start date must be before/)).not.toBeInTheDocument();
    });

    it('should show error when start date > end date', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-31');
      await userEvent.type(endDateInput, '2024-01-01');

      expect(
        screen.getByText('Start date must be before or equal to end date')
      ).toBeInTheDocument();
    });

    it('should allow start date equal to end date', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-15');
      await userEvent.type(endDateInput, '2024-01-15');

      expect(screen.queryByText(/Start date must be before/)).not.toBeInTheDocument();
    });

    it('should disable Apply button when dates are invalid', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const applyButton = screen.getByRole('button', { name: /Apply/ });
      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      // Initially disabled (no dates)
      expect(applyButton).toBeDisabled();

      // Enter only start date
      await userEvent.type(startDateInput, '2024-01-15');
      expect(applyButton).toBeDisabled();

      // Enter end date before start date
      await userEvent.type(endDateInput, '2024-01-01');
      expect(applyButton).toBeDisabled();
    });

    it('should enable Apply button when dates are valid', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const applyButton = screen.getByRole('button', { name: /Apply/ });
      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-01');
      await userEvent.type(endDateInput, '2024-01-31');

      expect(applyButton).not.toBeDisabled();
    });
  });

  describe('Apply Functionality', () => {
    it('should call onApply with correct dates when Apply is clicked', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');
      const applyButton = screen.getByRole('button', { name: /Apply/ });

      await userEvent.type(startDateInput, '2024-01-01');
      await userEvent.type(endDateInput, '2024-01-31');
      await userEvent.click(applyButton);

      expect(mockOnApply).toHaveBeenCalledOnce();
      const [startDate, endDate] = mockOnApply.mock.calls[0];
      expect(startDate.getFullYear()).toBe(2024);
      expect(startDate.getMonth()).toBe(0); // January
      expect(startDate.getDate()).toBe(1);

      expect(endDate.getFullYear()).toBe(2024);
      expect(endDate.getMonth()).toBe(0);
      expect(endDate.getDate()).toBe(31);
      expect(endDate.getHours()).toBe(23);
      expect(endDate.getMinutes()).toBe(59);
      expect(endDate.getSeconds()).toBe(59);
    });

    it('should not call onApply when dates are invalid', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');
      const applyButton = screen.getByRole('button', { name: /Apply/ });

      await userEvent.type(startDateInput, '2024-01-31');
      await userEvent.type(endDateInput, '2024-01-01');
      await userEvent.click(applyButton);

      expect(mockOnApply).not.toHaveBeenCalled();
      expect(screen.getByText(/Start date must be before/)).toBeInTheDocument();
    });

    it('should not call onApply when dates are not selected', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const applyButton = screen.getByRole('button', { name: /Apply/ });

      // Apply button is disabled, so we can't click it
      expect(applyButton).toBeDisabled();
      expect(mockOnApply).not.toHaveBeenCalled();
    });

    it('should set end time to end of day when applying', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');
      const applyButton = screen.getByRole('button', { name: /Apply/ });

      await userEvent.type(startDateInput, '2024-01-01');
      await userEvent.type(endDateInput, '2024-01-31');
      await userEvent.click(applyButton);

      const [, endDate] = mockOnApply.mock.calls[0];
      // End date should be 23:59:59.999
      expect(endDate.getHours()).toBe(23);
      expect(endDate.getMinutes()).toBe(59);
      expect(endDate.getSeconds()).toBe(59);
      expect(endDate.getMilliseconds()).toBe(999);
    });
  });

  describe('Clear Functionality', () => {
    it('should call onClear and reset dates when Clear is clicked', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      const endDateInput = screen.getByLabelText('End Date') as HTMLInputElement;
      const clearButton = screen.getByRole('button', { name: /Clear/ });

      await userEvent.type(startDateInput, '2024-01-01');
      await userEvent.type(endDateInput, '2024-01-31');

      expect(startDateInput.value).toBe('2024-01-01');
      expect(endDateInput.value).toBe('2024-01-31');

      await userEvent.click(clearButton);

      expect(mockOnClear).toHaveBeenCalledOnce();
      expect(startDateInput.value).toBe('');
      expect(endDateInput.value).toBe('');
    });

    it('should clear error messages when Clear is clicked', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-31');
      await userEvent.type(endDateInput, '2024-01-01');

      expect(screen.getByText(/Start date must be before/)).toBeInTheDocument();

      // Get all clear buttons and find the one that's the main Clear button (not X icon)
      const clearButtons = screen.getAllByLabelText('Clear date filter');
      const mainClearButton = clearButtons[clearButtons.length - 1]; // Last one is the main button
      await userEvent.click(mainClearButton);

      expect(screen.queryByText(/Start date must be before/)).not.toBeInTheDocument();
    });

    it('should clear selected preset when Clear is clicked', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const todayButton = screen.getByRole('button', { name: 'Today' });
      await userEvent.click(todayButton);

      expect(todayButton).toHaveAttribute('aria-pressed', 'true');

      // Get all clear buttons and find the one that's the main Clear button (not X icon)
      const clearButtons = screen.getAllByLabelText('Clear date filter');
      const mainClearButton = clearButtons[clearButtons.length - 1]; // Last one is the main button
      await userEvent.click(mainClearButton);

      expect(todayButton).toHaveAttribute('aria-pressed', 'false');
    });

    it('should show close button (X) when dates are selected', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      await userEvent.type(startDateInput, '2024-01-01');

      // Close button (X) should appear - look for the one in the header, not the main Clear button
      const closeButtons = screen.getAllByLabelText('Clear date filter');
      expect(closeButtons.length).toBeGreaterThanOrEqual(2); // X button + Clear button
    });
  });

  describe('Keyboard Navigation', () => {
    it('should apply filter when Enter key is pressed', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-01');
      await userEvent.type(endDateInput, '2024-01-31');

      // Focus the component and press Enter
      const filterRegion = screen.getByRole('region', { name: /Date range filter/ });
      fireEvent.keyDown(filterRegion, { key: 'Enter' });

      expect(mockOnApply).toHaveBeenCalledOnce();
    });

    it('should clear filter when Escape key is pressed', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      await userEvent.type(startDateInput, '2024-01-01');

      expect(startDateInput.value).toBe('2024-01-01');

      const filterRegion = screen.getByRole('region', { name: /Date range filter/ });
      fireEvent.keyDown(filterRegion, { key: 'Escape' });

      expect(mockOnClear).toHaveBeenCalledOnce();
      expect(startDateInput.value).toBe('');
    });
  });

  describe('Initial Values', () => {
    it('should populate date inputs with initial values', async () => {
      const startDate = new Date(2024, 0, 1); // January 1, 2024
      const endDate = new Date(2024, 0, 31); // January 31, 2024

      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
          initialStartDate={startDate}
          initialEndDate={endDate}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date') as HTMLInputElement;
      const endDateInput = screen.getByLabelText('End Date') as HTMLInputElement;

      expect(startDateInput.value).toBe('2024-01-01');
      expect(endDateInput.value).toBe('2024-01-31');
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels', () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      expect(screen.getByRole('region', { name: /Date range filter/ })).toBeInTheDocument();
      expect(screen.getByLabelText('Start Date')).toBeInTheDocument();
      expect(screen.getByLabelText('End Date')).toBeInTheDocument();
      expect(screen.getByLabelText('Apply date filter')).toBeInTheDocument();
      expect(screen.getByLabelText('Clear date filter')).toBeInTheDocument();
    });

    it('should associate error message with input fields', async () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      const startDateInput = screen.getByLabelText('Start Date');
      const endDateInput = screen.getByLabelText('End Date');

      await userEvent.type(startDateInput, '2024-01-31');
      await userEvent.type(endDateInput, '2024-01-01');

      // Error message should exist and be associated
      expect(screen.getByText(/Start date must be before/)).toBeInTheDocument();
      expect(screen.getByText(/Start date must be before/)).toHaveAttribute(
        'id',
        'date-error'
      );

      // Inputs should have aria-describedby
      expect(startDateInput).toHaveAttribute('aria-describedby', 'date-error');
      expect(endDateInput).toHaveAttribute('aria-describedby', 'date-error');
    });

    it('should have proper roles for buttons', () => {
      render(
        <DateRangeFilter
          onApply={mockOnApply}
          onClear={mockOnClear}
          isOpen={true}
        />
      );

      expect(screen.getByRole('button', { name: 'Today' })).toHaveAttribute(
        'aria-pressed'
      );
      expect(screen.getByRole('button', { name: 'This Week' })).toHaveAttribute(
        'aria-pressed'
      );
      expect(screen.getByRole('button', { name: 'This Month' })).toHaveAttribute(
        'aria-pressed'
      );
    });
  });
});
