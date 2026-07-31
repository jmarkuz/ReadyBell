/**
 * MSW Browser Setup
 * Enable mock API endpoints in the browser during development
 */

import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/**
 * Start the MSW worker in the browser
 * This intercepts fetch/XMLHttpRequest calls and routes them to mock handlers
 */
export const worker = setupWorker(...handlers);
