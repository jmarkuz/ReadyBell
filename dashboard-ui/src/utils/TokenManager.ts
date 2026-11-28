/** TokenManager - Secure Bearer Token Management */

export interface DecodedToken {
  sub?: string;
  id?: string;
  username?: string;
  email?: string;
  name?: string;
  role: 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN';
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

  /** Check if storage type is available (not disabled, not full) */
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

      const payload = parts[1];
      
      // Add padding if needed
      const padded = payload + '='.repeat((4 - payload.length % 4) % 4);
      
      const decoded = atob(padded);
      const parsed = JSON.parse(decoded);

      return parsed as DecodedToken;
    } catch (error) {
      console.error('Failed to decode JWT:', error);
      return null;
    }
  }

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

  private isTokenExpired(decodedToken: DecodedToken): boolean {
    if (!decodedToken.exp) {
      console.warn('Token missing exp claim');
      return true;
    }

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

      // Verify required claims exist. Platform admins are cross-tenant and carry no tenant_id.
      if (!decoded.role || (!decoded.tenant_id && decoded.role !== 'PLATFORM_ADMIN')) {
        console.warn('Token missing required claims (role, tenant_id)');
        return false;
      }

      // Verify role is one of the allowed roles (RESTAURANT_ADMIN is what the backend issues)
      const validRoles = ['STAFF', 'ADMIN', 'RESTAURANT_ADMIN', 'PLATFORM_ADMIN'];
      if (!validRoles.includes(decoded.role)) {
        console.warn(`Invalid role in token: ${decoded.role}`);
        return false;
      }

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
      
      storage.removeItem(this.storageKey);
      
      console.debug('Token cleared from storage');
    } catch (error) {
      console.error('Failed to clear token:', error);
    }
  }

  /**
   * Get decoded token payload for inspection
   * Returns token claims without verifying signature
   */
  public getDecodedToken(): DecodedToken | null {
    const token = this.get();
    if (!token) {
      return null;
    }

    return this.decodeJWT(token);
  }

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

  public isExpiringsSoon(thresholdSeconds: number = 300): boolean {
    const timeRemaining = this.getTimeUntilExpiry();
    return timeRemaining > 0 && timeRemaining <= thresholdSeconds;
  }

  public getTenantId(): string | null {
    const decoded = this.getDecodedToken();
    return decoded?.tenant_id || decoded?.tenantId || null;
  }

  public getRole(): string | null {
    const decoded = this.getDecodedToken();
    return decoded?.role || null;
  }

  public getUserId(): string | null {
    const decoded = this.getDecodedToken();
    return decoded?.sub || decoded?.id || decoded?.username || null;
  }
}

export const tokenManager = new TokenManager(true);
