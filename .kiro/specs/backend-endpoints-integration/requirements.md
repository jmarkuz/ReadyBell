# Requirements Document

## Introduction

The Backend Endpoints Integration feature extends the ReadyBell dashboard UI to fully integrate with backend API endpoints across all phases of platform functionality. Currently, Phase 1 (Order Management) is partially implemented with mocked data. This feature requires:

1. Completing Phase 1 integration with proper bearer token authentication and tenant isolation
2. Creating UI pages for Phase 2 (Restaurant Staff Admin) to manage staff accounts and configuration
3. Creating UI pages for Phase 3 (Platform Admin) to manage restaurants across the platform
4. Preparing infrastructure for Phase 4 (Customer PWA) sessions and push subscriptions
5. Implementing comprehensive error handling following RFC 7807
6. Enforcing multi-tenant isolation at every integration point
7. Ensuring real-time updates meet performance targets through SSE + polling fallback

The integration follows the multi-tenant architecture established in the restaurant-notification-platform backend, extending it to the frontend with proper authentication, authorization, and data isolation.

## Glossary

- **Dashboard_UI**: The web application used by Restaurant Staff, Restaurant Admins, and Platform Admins to manage orders, staffing, and tenants.
- **Backend_API**: The ReadyBell backend providing REST endpoints documented in BACKEND_ENDPOINTS_INTEGRATION.md.
- **Bearer_Token**: The JWT token issued by the backend authentication endpoint, transmitted in the Authorization header as `Bearer {token}`.
- **Tenant_Id**: The unique identifier for a Restaurant, used to isolate all data access and requests.
- **Tenant_Isolation**: The mechanism ensuring a user can only access data matching their Tenant_Id at the query layer and UI layer.
- **API_Client**: The frontend utility module (dashboard-ui/src/utils/api.ts) that manages HTTP requests, error handling, and authentication state.
- **SSE_Stream**: Server-Sent Events stream endpoint for real-time order updates (GET /api/orders/stream).
- **Polling_Fallback**: Polling-based updates for browsers without SSE support (GET /api/orders/poll with ETag).
- **Real_Time_Update**: The delay between a backend state change and the Dashboard_UI reflecting that change, measured in milliseconds.
- **Problem_Detail**: Error response format following RFC 7807 (urn:readybell:{context}:{error}).
- **Order_Lifecycle**: Valid order states and transitions: CREATED → IN_PROGRESS → READY → COMPLETED or CANCELLED.
- **Staff_Account**: A user account for Restaurant_Staff and Restaurant_Admin roles, scoped to a Tenant_Id.
- **Restaurant_Tenant**: A Restaurant record representing a tenant in the multi-tenant platform, managed by Platform_Admin.
- **Authentication**: The process of verifying credentials and issuing a Bearer_Token.
- **Authorization**: The process of verifying that a bearer token's scope (Tenant_Id, role) permits the requested action.
- **Optimistic_Lock**: A version field on records used to detect concurrent modifications (409 Conflict).

## Requirements

### Requirement 1: Bearer Token Authentication for Order Management

**User Story:** As a Restaurant Staff member, I want the dashboard to use bearer token authentication, so that my session is secure and only I can access my restaurant's data.

#### Acceptance Criteria

1. WHEN a Staff member logs in with valid credentials, THE Dashboard_UI SHALL store the returned Bearer_Token in secure client storage (localStorage with httpOnly simulation or sessionStorage).
2. WHEN the Dashboard_UI makes a request to a protected endpoint, THE API_Client SHALL include the Bearer_Token in the Authorization header as `Authorization: Bearer {token}`.
3. IF a request returns a 401 Unauthorized response, THEN THE Dashboard_UI SHALL discard the stored Bearer_Token, redirect the user to the login page, and display an authentication error message.
4. IF a Bearer_Token becomes invalid (expired or revoked), THEN THE API_Client SHALL refresh the token using a refresh token endpoint OR redirect to login on the next request.
5. WHEN a Staff member logs out, THE Dashboard_UI SHALL delete the stored Bearer_Token and invalidate the session.
6. THE Dashboard_UI SHALL transmit all bearer tokens over HTTPS only and reject HTTP requests.
7. WHEN the Dashboard_UI initializes, THE API_Client SHALL verify the stored Bearer_Token is valid before allowing access to protected endpoints.

### Requirement 2: Phase 1 Order Management Real-Time Updates

**User Story:** As a Restaurant Staff member, I want live order updates in the dashboard, so that I see status changes within seconds without manual refresh.

#### Acceptance Criteria

1. WHEN a Staff member views the orders list, THE Dashboard_UI SHALL establish a real-time connection using SSE (GET /api/orders/stream with Bearer_Token).
2. WHEN the Backend_API publishes an order event (OrderCreated, OrderStatusUpdated, OrderReady), THE Dashboard_UI SHALL receive the event through the SSE stream within 2 seconds and update the order list.
3. IF the SSE connection fails or is not supported, THE Dashboard_UI SHALL fall back to polling (GET /api/orders/poll) every 10 seconds with ETag support.
4. WHEN using the polling fallback, THE Dashboard_UI SHALL use the ETag header to avoid re-fetching unchanged order data, receiving 304 Not Modified when appropriate.
5. WHILE the SSE stream is active, THE Dashboard_UI SHALL heartbeat the connection every 30 seconds to detect dead connections and reconnect within 5 seconds of detecting closure.
6. WHEN the SSE stream reconnects after an interruption, THE Dashboard_UI SHALL sync the complete order list to ensure no events were missed.
7. THE Real_Time_Update delay between order status change on the backend and the Dashboard_UI reflecting that change SHALL be less than 2 seconds under normal network conditions.

### Requirement 3: Order List Pagination, Filtering, and Sorting

**User Story:** As a Restaurant Staff member, I want to filter and sort orders, so that I can find specific orders quickly and focus on high-priority items.

#### Acceptance Criteria

1. WHEN the Dashboard_UI requests the orders list, THE API_Client SHALL support query parameters for pagination (page, pageSize), filtering (status, search), and sorting (sortBy, sortOrder).
2. WHEN a Staff member requests page N with pageSize P, THE Backend_API SHALL return P items from the Nth page and include totalCount, pageCount, and currentPage in the response.
3. WHEN a Staff member filters by status (CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED), THE Backend_API SHALL return only orders matching the selected statuses (multiple may be selected).
4. WHEN a Staff member searches by keyword, THE Backend_API SHALL search orderId, customerName, and items fields and return matching orders within 1 second for datasets up to 10,000 orders.
5. WHEN a Staff member requests sorting by createdAt, eta, or status, THE Backend_API SHALL return orders sorted accordingly in ascending or descending order.
6. THE Dashboard_UI SHALL preserve filter and sort state in URL query parameters, allowing users to bookmark filtered views.
7. WHEN a Staff member requests pageSize > 100, THE API_Client SHALL cap the request to 100 items per page (backend enforces this) and display a client-side warning if attempted.

### Requirement 4: Order Status Transitions with Conflict Detection

**User Story:** As a Restaurant Staff member, I want to update order status safely, so that concurrent updates don't create conflicting states.

#### Acceptance Criteria

1. WHEN a Staff member requests a status transition (e.g., CREATED → IN_PROGRESS), THE Dashboard_UI SHALL validate the transition is allowed per the Order_Lifecycle before sending the request.
2. WHEN the Backend_API receives a status transition request, THE Backend_API SHALL enforce the Order_Lifecycle server-side and reject invalid transitions (e.g., COMPLETED → IN_PROGRESS).
3. IF a concurrent modification is detected (another user changed the order while this user was viewing it), THEN THE Backend_API SHALL return 409 Conflict with the current order state and version info.
4. WHEN THE Dashboard_UI receives 409 Conflict, THE Dashboard_UI SHALL display a conflict resolution dialog showing the current order state and allowing the user to retry or refresh and see the current state.
5. THE Dashboard_UI SHALL display the allowed next states for the current order status as button options in the UI, disabling invalid transitions.
6. WHEN the order status changes to READY, THE Dashboard_UI SHALL highlight the order with a visual indicator (color, animation, sound notification) to notify staff that the order is ready for pickup.

### Requirement 5: Multi-Tenant Data Isolation and Validation

**User Story:** As a Platform operator, I want strict tenant isolation, so that Staff members only access their restaurant's data and see 403 errors for cross-tenant access attempts.

#### Acceptance Criteria

1. WHEN a Staff member makes a request to the Backend_API, THE API_Client SHALL include the Bearer_Token which encodes the user's Tenant_Id and role.
2. THE Backend_API SHALL extract the Tenant_Id from the Bearer_Token and filter all queries to only return records matching that Tenant_Id.
3. IF a Staff member attempts to access data for a different Tenant_Id (e.g., via direct API call with a manipulated query parameter), THE Backend_API SHALL return 403 Forbidden with a Problem_Detail error indicating tenant mismatch.
4. WHEN THE Dashboard_UI receives 403 Forbidden, THE Dashboard_UI SHALL display an error message and prevent the requested action without displaying data from the inaccessible tenant.
5. THE Dashboard_UI SHALL validate the Bearer_Token's Tenant_Id before allowing any data-access operation and warn the user if tenant context is missing.
6. WHEN a Staff member from Restaurant A attempts to view orders for Restaurant B (cross-tenant), THE Dashboard_UI SHALL not send the request and display a client-side error.

### Requirement 6: RFC 7807 Problem Detail Error Handling

**User Story:** As a developer, I want standardized error responses, so that the frontend can handle errors consistently across all endpoints.

#### Acceptance Criteria

1. WHEN the Backend_API returns an error, THE API_Client SHALL parse the Problem_Detail response (urn:readybell:{context}:{error}) with fields: type, title, status, detail, and optional instance.
2. WHEN an API_Client receives an error response, THE API_Client SHALL map the error type to a user-friendly message and log the error with appropriate severity (warning for 4xx, error for 5xx).
3. THE Dashboard_UI SHALL display error messages to the user based on the Problem_Detail detail field, not raw technical details.
4. WHEN THE Dashboard_UI receives a 400 Bad Request, THE Dashboard_UI SHALL parse the detail field and display specific validation errors (e.g., "Invalid status filter value").
5. WHEN THE Dashboard_UI receives a 401 Unauthorized (urn:readybell:auth:invalid-token), THE Dashboard_UI SHALL redirect to login and clear the stored Bearer_Token.
6. WHEN THE Dashboard_UI receives a 403 Forbidden (urn:readybell:auth:tenant-mismatch), THE Dashboard_UI SHALL display a "Access Denied" message and prevent the action.
7. WHEN THE Dashboard_UI receives a 404 Not Found, THE Dashboard_UI SHALL display "Order not found" or similar message and remove the missing item from the UI.
8. WHEN THE Dashboard_UI receives a 409 Conflict, THE Dashboard_UI SHALL trigger conflict resolution (refresh order state and prompt user to retry).
9. WHEN THE Dashboard_UI receives a 5xx Server Error, THE Dashboard_UI SHALL display a generic error message and offer to retry or contact support.
10. THE API_Client error handler SHALL NOT expose sensitive information (e.g., database errors, internal paths) to the user.

### Requirement 7: Phase 2 Staff Admin Page - Staff Account Management

**User Story:** As a Restaurant Admin, I want to manage staff accounts, so that I can create accounts, assign roles, and deactivate staff when they leave.

#### Acceptance Criteria

1. WHEN a Restaurant_Admin accesses the Staff Admin page, THE Dashboard_UI SHALL request the list of staff accounts for that admin's Tenant_Id using Bearer_Token authorization.
2. THE Dashboard_UI SHALL display all staff accounts in a table showing username, email, name, role (STAFF or ADMIN), status (ACTIVE or INACTIVE), and creation date.
3. WHEN a Restaurant_Admin clicks "Create Staff Account", THE Dashboard_UI SHALL open a form with fields: username, email, password, name, and role selection (STAFF or ADMIN).
4. WHEN the admin submits the form with valid data, THE Dashboard_UI SHALL POST to /api/admin/staff with the staff data in the request body.
5. IF the backend returns 400 Bad Request (e.g., duplicate username), THE Dashboard_UI SHALL display the validation error from the Problem_Detail detail field and keep the form open.
6. WHEN the staff account is created successfully (201 Created), THE Dashboard_UI SHALL display a success notification and refresh the staff list to show the new account.
7. WHEN a Restaurant_Admin clicks "Deactivate" for a staff member, THE Dashboard_UI SHALL prompt for confirmation before sending DELETE /api/admin/staff/{accountId}.
8. WHEN the staff account is deactivated (204 No Content), THE Dashboard_UI SHALL remove the account from the list and display a success notification.
9. IF the admin attempts to deactivate a staff member from a different tenant, THE Backend_API SHALL return 403 Forbidden and the action shall be blocked.

### Requirement 8: Phase 2 Staff Admin Page - Restaurant Configuration

**User Story:** As a Restaurant Admin, I want to update restaurant configuration, so that order prep times and contact information are current.

#### Acceptance Criteria

1. WHEN a Restaurant_Admin accesses the Restaurant Settings page within the Staff Admin section, THE Dashboard_UI SHALL fetch the current restaurant configuration using GET /api/admin/config with Bearer_Token.
2. THE Dashboard_UI SHALL display a form with fields: restaurantName, phoneNumber, address, cuisineType, averagePrepTime (in seconds), and optional webhookUrl.
3. WHEN the admin modifies any field and clicks Save, THE Dashboard_UI SHALL validate the data client-side (e.g., non-empty name, valid phone format).
4. WHEN all fields are valid, THE Dashboard_UI SHALL send PUT /api/admin/config with the updated configuration.
5. IF the backend returns 400 Bad Request (e.g., invalid prep time), THE Dashboard_UI SHALL display the validation error from the Problem_Detail detail field.
6. WHEN the configuration is updated successfully (200 OK), THE Dashboard_UI SHALL display a success notification and refresh the displayed configuration.
7. IF the admin attempts to update configuration for a different tenant, THE Backend_API SHALL return 403 Forbidden and block the operation.
8. WHEN the averagePrepTime is updated, THE Dashboard_UI SHALL refresh the ETA display in the orders list (if visible) to reflect the new estimate.

### Requirement 9: Phase 3 Platform Admin Page - Restaurant Tenant Management

**User Story:** As a Platform Admin, I want to manage restaurants as tenants, so that I can onboard new restaurants and control their status.

#### Acceptance Criteria

1. WHEN a Platform_Admin accesses the Platform Admin page, THE Dashboard_UI SHALL request the list of all restaurants using GET /api/platform/restaurants with Bearer_Token authorization.
2. THE Dashboard_UI SHALL display all restaurants in a table showing tenantId, restaurantName, status (ACTIVE or INACTIVE), and creation date.
3. WHEN a Platform_Admin clicks "Create Restaurant", THE Dashboard_UI SHALL open a form with fields: restaurantName, ownerEmail, and ownerName.
4. WHEN the admin submits the form with valid data, THE Dashboard_UI SHALL POST to /api/platform/restaurants with the restaurant data.
5. IF the backend returns 400 Bad Request (missing required fields or invalid email), THE Dashboard_UI SHALL display the validation error and keep the form open.
6. WHEN the restaurant is created successfully (201 Created), THE Dashboard_UI SHALL display the assigned tenantId and a success notification, then refresh the restaurant list.
7. WHEN a Platform_Admin clicks "Edit" for a restaurant, THE Dashboard_UI SHALL open a form pre-filled with restaurantName and allow status changes (ACTIVE ↔ INACTIVE).
8. WHEN the admin submits updates, THE Dashboard_UI SHALL send PUT /api/platform/restaurants/{tenantId} with the updated data.
9. WHEN a Platform_Admin clicks "Deactivate" for a restaurant, THE Dashboard_UI SHALL prompt for confirmation (warning that existing orders will be impacted) before sending DELETE /api/platform/restaurants/{tenantId}.
10. WHEN the restaurant is deactivated (204 No Content), THE Dashboard_UI SHALL remove the restaurant from the list and display a success notification.
11. WHEN a Platform_Admin deactivates a restaurant, the Backend_API SHALL reject new Customer_Session starts for that tenant with an error indicating the restaurant is inactive.

### Requirement 10: Bearer Token Lifecycle Management

**User Story:** As a system, I want to manage token expiry and refresh gracefully, so that users stay logged in within security constraints.

#### Acceptance Criteria

1. THE Backend_API SHALL issue Bearer_Tokens with a configurable expiry time (default 1 hour) in the JWT exp claim.
2. WHEN a Bearer_Token expires, THE API_Client SHALL detect the 401 Unauthorized response and attempt to refresh the token using a refresh token endpoint (if implemented) OR redirect to login.
3. IF a refresh token is available and valid, THE API_Client SHALL silently refresh and retry the failed request without user intervention.
4. IF a refresh token is not available or refresh fails, THE API_Client SHALL redirect the user to the login page with a session-expired message.
5. WHEN a user is inactive for 30 minutes (configurable), THE Dashboard_UI MAY proactively log the user out for security.
6. WHEN a user logs out explicitly, THE Dashboard_UI SHALL DELETE the stored Bearer_Token and redirect to the login page.
7. THE Dashboard_UI SHALL display the time until token expiry (optional) and offer a "Renew Session" button to refresh before expiry.

### Requirement 11: Optimistic UI Updates for Order Status Changes

**User Story:** As a Restaurant Staff member, I want order status changes to update immediately in the UI, so that the interface feels responsive.

#### Acceptance Criteria

1. WHEN a Staff member clicks a status transition button (e.g., "Mark as Ready"), THE Dashboard_UI SHALL immediately update the displayed order status in the UI (optimistic update).
2. IF the backend request succeeds (200 OK), THE Dashboard_UI SHALL confirm the update matches the backend state and continue.
3. IF the backend request fails (4xx or 5xx error), THE Dashboard_UI SHALL revert the optimistic update to the previous status and display an error message.
4. IF the backend request fails with 409 Conflict (concurrent modification), THE Dashboard_UI SHALL show a conflict resolution dialog with the current backend state and allow the user to retry or refresh.
5. WHEN an order is updated through the SSE stream by another user, THE Dashboard_UI SHALL update the order state to reflect the server's current state (which may override a pending optimistic update).

### Requirement 12: Real-Time Update Latency and Performance

**User Story:** As a Restaurant Staff member, I want responsive dashboard updates, so that I don't miss critical order ready events.

#### Acceptance Criteria

1. WHEN an order status changes on the backend to READY, THE Dashboard_UI SHALL reflect the status change visually within 2 seconds (measured from backend state change to Dashboard_UI display update).
2. WHEN the Dashboard_UI receives an OrderReady event through SSE, THE Dashboard_UI SHALL display a visual indicator (color, icon, animation) and optionally play a notification sound.
3. THE SSE connection SHALL maintain a heartbeat every 30 seconds to detect dead connections, reconnecting within 5 seconds if lost.
4. WHEN polling falls back (SSE unavailable), THE Dashboard_UI SHALL poll every 10 seconds and parse the ETag response to minimize bandwidth usage.
5. THE orders list page (with 25 items per page) SHALL load and display within 1 second for the first page under normal network conditions (> 1 Mbps).
6. WHEN filtering or sorting, THE Backend_API SHALL return results within 1 second for datasets up to 10,000 orders.
7. WHEN a Staff member navigates between pages, THE Dashboard_UI SHALL show a loading indicator and maintain scroll position or focus after page load completes.

### Requirement 13: Bearer Token Validation and Authorization Checks

**User Story:** As a security-conscious system, I want to validate tokens and enforce role-based access control, so that users can only perform actions matching their role.

#### Acceptance Criteria

1. WHEN the Dashboard_UI initializes, THE API_Client SHALL validate the stored Bearer_Token by decoding the JWT payload (without trusting the signature, client-side validation only) to check expiry and role claims.
2. WHEN a Staff member (role: STAFF) attempts to access the Platform Admin page, THE Dashboard_UI SHALL check the token's role claim and display "Access Denied" without sending a request.
3. WHEN a Restaurant_Admin (role: ADMIN for a specific Tenant_Id) attempts to access the Platform Admin page, THE Dashboard_UI SHALL deny access and display "Access Denied".
4. WHEN a Platform_Admin (role: PLATFORM_ADMIN) attempts to access a Restaurant Admin page for a specific Tenant_Id, THE Dashboard_UI SHALL allow access (Platform_Admin has administrative privilege across all tenants).
5. IF a request is made with an invalid or malformed Bearer_Token, THE Backend_API SHALL return 401 Unauthorized with a Problem_Detail indicating "Invalid or expired token".
6. WHEN THE Dashboard_UI receives 401 Unauthorized for any reason, THE Dashboard_UI SHALL immediately clear the stored Bearer_Token and redirect to login.

### Requirement 14: Phase 2 and Phase 3 UI Page Structure

**User Story:** As a user, I want organized admin pages, so that I can easily navigate between staff management, settings, and tenant management.

#### Acceptance Criteria

1. THE Dashboard_UI SHALL provide a navigation menu with links to Staff Admin (Phase 2) and Platform Admin (Phase 3) pages based on the user's role.
2. WHEN a Restaurant_Admin logs in, THE Dashboard_UI SHALL display the Staff Admin page (with Staff Accounts and Restaurant Settings sections) and hide the Platform Admin link.
3. WHEN a Platform_Admin logs in, THE Dashboard_UI SHALL display the Platform Admin page (Restaurant Management section) and hide the Staff Admin link.
4. THE Staff Admin page SHALL have two sections: "Staff Accounts" (table with create/deactivate actions) and "Restaurant Settings" (form with update action).
5. THE Platform Admin page SHALL have one section: "Manage Restaurants" (table with create/edit/deactivate actions).
6. EACH admin page SHALL display a breadcrumb navigation showing the current location (e.g., "Dashboard > Staff Admin > Staff Accounts").
7. WHEN an admin action completes successfully, THE Dashboard_UI SHALL display a success toast notification (e.g., "Staff account created successfully").
8. WHEN an admin action fails, THE Dashboard_UI SHALL display an error toast notification with the specific error message from the Backend_API Problem_Detail.

### Requirement 15: API Client Request and Response Interceptors

**User Story:** As a developer, I want centralized request/response handling, so that authentication, error handling, and retry logic are consistent across all endpoints.

#### Acceptance Criteria

1. THE API_Client SHALL intercept all outgoing requests to inject the Bearer_Token in the Authorization header and set Content-Type: application/json.
2. THE API_Client SHALL intercept all responses and parse Problem_Detail errors (urn:readybell:...) to extract type, title, status, and detail fields.
3. WHEN a response has status 401, THE API_Client SHALL trigger token refresh or redirect to login before the Dashboard_UI sees the error.
4. WHEN a response has status 5xx with retriable nature (e.g., 503 Service Unavailable), THE API_Client SHALL retry the request up to 3 times with exponential backoff (1s, 2s, 4s).
5. THE API_Client SHALL log all requests and responses (with tokens redacted) for debugging purposes.
6. WHEN the Dashboard_UI calls an API function (e.g., getOrders()), THE API_Client response shall include both data and error fields for the caller to handle appropriately.

### Requirement 16: Secure Bearer Token Storage

**User Story:** As a security-conscious user, I want my bearer token stored securely, so that it cannot be accessed by malicious scripts.

#### Acceptance Criteria

1. THE Dashboard_UI SHALL store the Bearer_Token in a secure storage mechanism (localStorage with a security note, or sessionStorage for maximum session-based isolation).
2. IF possible, THE Dashboard_UI SHALL avoid storing tokens in localStorage and instead use httpOnly cookies (managed by the backend) to prevent XSS attacks from accessing the token.
3. WHEN the page unloads or the user closes the browser, THE Dashboard_UI SHALL clear the Bearer_Token from session-based storage.
4. THE Dashboard_UI SHALL NEVER transmit Bearer_Tokens in URL query parameters or request body for logged requests.
5. WHEN the user logs out, THE Dashboard_UI SHALL securely delete the stored Bearer_Token (overwrite before deleting if possible).
6. THE Dashboard_UI SHALL reject any attempt to access local/session storage from browser DevTools or external scripts by using Content-Security-Policy headers (backend-managed).

