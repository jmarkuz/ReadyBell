/**
 * TokenManager - Secure Bearer Token Management
 * 
 * Handles:
 * - Storage of JWT bearer tokens in secure storage
 * - Retrieval of stored tokens
 * - Token validation (expiry, claims)
 * - Secure token clearing/revocation
 * 
 * Storage Strategy:
 * - Primary: sessionStorage (cleared on browser close, session-based isolation)
 * - Fallback: localStorage (persistent, but less secure)
 * 
 * Token Format: JWT with claims (iat, exp, role, tenant_id)
 */

export interface DecodedToken {
  sub?: string;
  id?: string;
  username?: string;
  email?: string;
  name?: string;
  role: 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN';
  tenant_id: string;
  tenantId?: string;
  iat: number;
  exp: number;
}

export class TokenManager {
  private readonly storageKey = 'auth-token';
  private readonly storageType: 'sessionStorage' | 'localStorage';

  /**
   * Initialize TokenManager
   * Uses sessionStorage by default (more secure, cleared on browser close)
   * Falls back to localStorage if sessionStorage unavailable
   */
  constructor(useSessionStorage: boolean = true) {
    this.storageType = useSessionStorage ? 'sessionStorage' : 'localStorage';
    
    // Test if storage is available
    if (!this.isStorageAvailable(this.storageType)) {
      console.warn(
        `${this.storageType} not available, attempting fallback`,
      );
      this.storageType = this.storageType === 'sessionStorage' 
        ? 'localStorage' 
        : 'sessionStorage';
      
      if (!this.isStorageAvailable(this.storageType)) {
        console.error('Neither sessionStorage nor localStorage is available');
      }
    }
  }

  /**
   * Check if storage type is available (not disabled, not full)
   */
  private isStorageAvailable(storageType: 'sessionStorage' | 'localStorage'): boolean {
    try {
      const storage = storageType === 'sessionStorage' ? sessionStorage : localStorage;
      const testKey = '__test__';
      storage.setItem(testKey, testKey);
      storage.removeItem(testKey);
      return true;
    } catch (error) {
      return false;
    }
  }

  /**
   * Get the appropriate storage object
   */
  private getStorage(): Storage {
    return this.storageType === 'sessionStorage' ? sessionStorage : localStorage;
  }

  /**
   * Decode JWT payload without verifying signature (client-side only)
   * Note: Signature verification must happen on backend
   */
  private decodeJWT(token: string): DecodedToken | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        console.error('Invalid JWT format: expected 3 parts');
        return null;
      }

      // Decode payload (second part)
      const payload = parts[1];
      
      // Add padding if needed
      const padded = payload + '='.repeat((4 - payload.length % 4) % 4);
      
      // Decode from base64url
      const decoded = atob(padded);
      const parsed = JSON.parse(decoded);

      return parsed as DecodedToken;
    } catch (error) {
      console.error('Failed to decode JWT:', error);
      return null;
    }
  }

  /**
   * Store bearer token in secure storage
   * 
   * @param token - JWT bearer token from authentication endpoint
   * @returns true if storage succeeded, false otherwise
   */
  public store(token: string): boolean {
    if (!token || typeof token !== 'string') {
      console.error('Invalid token: must be a non-empty string');
      return false;
    }

    try {
      // Validate token format before storing
      const decoded = this.decodeJWT(token);
      if (!decoded) {
        console.error('Token validation failed during store');
        return false;
      }

      // Check expiry before storing
      if (this.isTokenExpired(decoded)) {
        console.warn('Cannot store expired token');
        return false;
      }

      // Store token securely
      const storage = this.getStorage();
      storage.setItem(this.storageKey, token);
      
      // Log success without exposing token
      console.debug(`Token stored successfully in ${this.storageType}`);
      return true;
    } catch (error) {
      console.error('Failed to store token:', error);
      return false;
    }
  }

  /**
   * Retrieve stored bearer token
   * 
   * @returns token if found and valid, null otherwise
   */
  public get(): string | null {
    try {
      const storage = this.getStorage();
      const token = storage.getItem(this.storageKey);

      if (!token) {
        return null;
      }

      // Validate token before returning
      const decoded = this.decodeJWT(token);
      if (!decoded) {
        console.warn('Stored token is invalid, clearing it');
        this.clear();
        return null;
      }

      // Check expiry
      if (this.isTokenExpired(decoded)) {
        console.warn('Stored token is expired, clearing it');
        this.clear();
        return null;
      }

      return token;
    } catch (error) {
      console.error('Failed to retrieve token:', error);
      return null;
    }
  }

  /**
   * Check if token is expired
   * 
   * @param decodedToken - Decoded JWT payload
   * @returns true if token is expired or expires within 30 seconds, false otherwise
   */
  private isTokenExpired(decodedToken: DecodedToken): boolean {
    if (!decodedToken.exp) {
      console.warn('Token missing exp claim');
      return true;
    }

    // Convert exp (seconds since epoch) to milliseconds
    const expiryTime = decodedToken.exp * 1000;
    const currentTime = Date.now();
    
    // Consider token expired if it expires within 30 seconds (grace period)
    const gracePeriod = 30 * 1000;
    const isExpired = currentTime >= (expiryTime - gracePeriod);

    return isExpired;
  }

  /**
   * Validate stored token
   * Checks:
   * - Token exists
   * - Token is properly formatted (valid JWT)
   * - Token is not expired
   * - Token has required claims (role, tenant_id)
   * 
   * @returns true if token is valid and usable, false otherwise
   */
  public validate(): boolean {
    try {
      const token = this.get();
      if (!token) {
        return false;
      }

      const decoded = this.decodeJWT(token);
      if (!decoded) {
        return false;
      }

      // Verify required claims exist
      if (!decoded.role || !decoded.tenant_id) {
        console.warn('Token missing required claims (role, tenant_id)');
        return false;
      }

      // Verify role is one of the allowed roles
      const validRoles = ['STAFF', 'ADMIN', 'PLATFORM_ADMIN'];
      if (!validRoles.includes(decoded.role)) {
        console.warn(`Invalid role in token: ${decoded.role}`);
        return false;
      }

      // Check expiry
      if (this.isTokenExpired(decoded)) {
        return false;
      }

      return true;
    } catch (error) {
      console.error('Token validation failed:', error);
      return false;
    }
  }

  /**
   * Clear stored bearer token
   * Securely removes token from storage
   */
  public clear(): void {
    try {
      const storage = this.getStorage();
      
      // Overwrite storage entry before deleting (optional security measure)
      storage.setItem(this.storageKey, '');
      
      // Remove from storage
      storage.removeItem(this.storageKey);
      
      console.debug('Token cleared from storage');
    } catch (error) {
      console.error('Failed to clear token:', error);
    }
  }

  /**
   * Get decoded token payload for inspection
   * Returns token claims without verifying signature
   * 
   * @returns Decoded token payload or null if token invalid/expired
   */
  public getDecodedToken(): DecodedToken | null {
    const token = this.get();
    if (!token) {
      return null;
    }

    return this.decodeJWT(token);
  }

  /**
   * Get time until token expiry in seconds
   * 
   * @returns Seconds until expiry, or -1 if token expired or invalid
   */
  public getTimeUntilExpiry(): number {
    const decoded = this.getDecodedToken();
    if (!decoded?.exp) {
      return -1;
    }

    const expiryTime = decoded.exp * 1000;
    const currentTime = Date.now();
    const secondsRemaining = Math.floor((expiryTime - currentTime) / 1000);

    return Math.max(0, secondsRemaining);
  }

  /**
   * Check if token is expiring soon
   * 
   * @param thresholdSeconds - Warning threshold in seconds (default 5 minutes)
   * @returns true if token expires within threshold, false otherwise
   */
  public isExpiringsSoon(thresholdSeconds: number = 300): boolean {
    const timeRemaining = this.getTimeUntilExpiry();
    return timeRemaining > 0 && timeRemaining <= thresholdSeconds;
  }

  /**
   * Get tenant ID from current token
   * 
   * @returns Tenant ID or null if token invalid/expired
   */
  public getTenantId(): string | null {
    const decoded = this.getDecodedToken();
    return decoded?.tenant_id || decoded?.tenantId || null;
  }

  /**
   * Get user role from current token
   * 
   * @returns User role or null if token invalid/expired
   */
  public getRole(): string | null {
    const decoded = this.getDecodedToken();
    return decoded?.role || null;
  }

  /**
   * Get user ID from current token
   * 
   * @returns User ID or null if token invalid/expired
   */
  public getUserId(): string | null {
    const decoded = this.getDecodedToken();
    return decoded?.sub || decoded?.id || decoded?.username || null;
  }
}

/**
 * Create singleton instance
 */
export const tokenManager = new TokenManager(true);
