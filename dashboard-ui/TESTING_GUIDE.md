# Dashboard UI Testing Guide

## Development Server Status

✅ **Dev Server is Running**
- **URL**: http://localhost:5173/
- **Port**: 5173
- **Status**: Ready for testing

## How to Access the Dashboard

Open your browser and navigate to:
```
http://localhost:5173/
```

## Key Features to Test

### 1. Theme Switching
- **Location**: Header (top-right corner)
- **Test**: 
  - Click the sun/moon icon to toggle between light and dark modes
  - Theme should persist after page reload (check localStorage)
  - All colors should change appropriately
- **Expected**: Instant theme change, persistence across sessions

### 2. Layout & Navigation
- **Components**: 
  - Header (top)
  - Sidebar (left)
  - Main content area
- **Test**:
  - Click navigation links to switch pages
  - Sidebar should be responsive on mobile
  - Logo link returns to Dashboard
- **Expected**: Smooth navigation, active state highlighting

### 3. Order Dashboard (Main Page)
- **Components**:
  - Order Summary Cards (4 cards: Total, New, Preparing, Ready)
  - Incoming Orders Table with mock data
- **Test**:
  - Cards display realistic metrics
  - Table shows 10-25 orders with proper formatting
  - Status badges show correct colors
- **Expected**: Cards display smoothly, table is scrollable

### 4. Table Features
- **Sorting**: Click column headers to sort
- **Pagination**: Use page controls at bottom
- **Status Colors**: 
  - Blue: New orders (CREATED)
  - Orange: Preparing (PREPARING)
  - Green: Ready for pickup (READY)
  - Gray: Completed (COMPLETED)
- **Test**: Click headers, navigate pages, verify colors
- **Expected**: Sorting works, pagination navigates correctly

### 5. Search & Filters
- **Search**: Type in header search box
- **Filters**: Status filter dropdown (if implemented)
- **Test**: Search for orders, filter by status
- **Expected**: Table updates based on search/filter

### 6. Keyboard Navigation
- **Test**:
  - Tab through elements (should skip hidden elements)
  - Theme toggle: press Enter or Space when focused
  - Search input: press Enter to search
  - Table: Tab to column headers, press Enter to sort
- **Expected**: All interactive elements accessible via keyboard

### 7. Responsive Design
- **Mobile (< 768px)**: 
  - Sidebar collapses into hamburger menu
  - Order cards stack vertically
  - Table scrolls horizontally
- **Tablet (768px - 1024px)**:
  - Cards display 2 columns
  - Sidebar visible or collapsible
- **Desktop (> 1024px)**:
  - Full layout with sidebar and main content
  - 4-column card grid
- **Test**: 
  - Open DevTools (F12)
  - Toggle device emulation (Ctrl+Shift+M)
  - Test at 320px, 768px, 1024px widths
- **Expected**: Layouts adapt smoothly at breakpoints

### 8. Color Contrast
- **Test**: 
  - Read text clearly in light mode
  - Read text clearly in dark mode
  - Status badges have sufficient contrast
- **Expected**: All text meets WCAG AA standards (4.5:1 ratio)

### 9. Focus Indicators
- **Test**:
  - Tab through interactive elements
  - Each focused element should have visible outline/ring
  - Focus indicator should have good contrast
- **Expected**: Clear visual indication of focus state

### 10. Performance
- **Test**:
  - Dashboard loads in < 3 seconds
  - No lag when toggling theme
  - Table sorting is smooth
  - Search debounces properly (300ms)
- **Expected**: Smooth 60fps interactions

## Testing Browsers

Recommended browsers for testing:
- Chrome 90+ (latest stable)
- Firefox 88+ (latest stable)
- Safari 14+ (latest stable)
- Edge 90+ (latest stable)

## Running Tests

### Unit Tests
```bash
npm test
```

### Integration Tests (Keyboard Navigation)
```bash
npm test -- KeyboardNavigation.integration.test.tsx
```

### All Tests
```bash
npm test
```

## Quick Debugging Tips

1. **Check Console**: Open DevTools → Console tab for any errors
2. **Inspect Elements**: Right-click element → Inspect to check HTML/CSS
3. **Storage**: DevTools → Application → Local Storage to check theme persistence
4. **Network**: DevTools → Network tab to check API calls
5. **Performance**: DevTools → Performance tab to record interactions

## Common Test Scenarios

### Test Case 1: Complete Workflow
1. Load dashboard
2. Toggle theme to dark
3. Reload page (verify theme persists)
4. Click sidebar navigation link
5. Use search to filter orders
6. Sort table by clicking column header
7. Navigate pagination

### Test Case 2: Keyboard Navigation
1. Tab through all elements
2. Press Enter/Space on buttons
3. Use arrow keys in dropdowns
4. Press Escape to close menus
5. Verify no keyboard traps

### Test Case 3: Mobile Experience
1. Resize to 375px (mobile)
2. Click hamburger menu
3. Navigate using mobile layout
4. Test touch targets (44x44px minimum)
5. Verify horizontal scroll on table only

### Test Case 4: Accessibility
1. Tab to each element
2. Verify focus indicators visible
3. Check color contrast ratios
4. Verify aria-labels present
5. Test with screen reader (optional)

## Reporting Issues

If you find issues during testing, note:
1. Browser name and version
2. Screen size / device type
3. Steps to reproduce
4. Expected vs. actual behavior
5. Screenshot if relevant

## API Integration

The dashboard uses mock data via MSW (Mock Service Worker). To view mock endpoints:
- `/api/orders` - Get list of orders
- `/api/orders/stream` - SSE stream (real-time updates)
- `/api/orders/{id}/status` - Update order status

Mock data is generated with realistic values for testing purposes.

## Next Steps

1. **Manual Testing**: Use testing scenarios above
2. **Automated Tests**: Run `npm test` for unit/integration tests
3. **Accessibility Audit**: Use Lighthouse or axe DevTools
4. **Performance Test**: Use Chrome DevTools Lighthouse
5. **Real Device**: Test on actual mobile devices if possible

## Support

For questions about specific features, check:
- README.md - Project setup and commands
- Design document - Component architecture
- Requirements document - Feature specifications
