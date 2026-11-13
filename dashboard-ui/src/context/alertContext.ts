import { createContext, useContext } from 'react';

export interface NewOrderAlerts {
  /** Sound and vibration for new orders; off until staff turn it on (browsers need that gesture anyway). */
  soundEnabled: boolean;
  setSoundEnabled: (on: boolean) => void;
  /** A new order arrived: announces it to screen readers, counts it in the tab title, beeps if enabled. */
  notifyNewOrder: (orderNumber?: string) => void;
}

/** Without a provider (tests, other pages) the alerts do nothing. */
const inactive: NewOrderAlerts = { soundEnabled: false, setSoundEnabled: () => undefined, notifyNewOrder: () => undefined };

export const AlertContext = createContext<NewOrderAlerts>(inactive);

export function useNewOrderAlerts(): NewOrderAlerts {
  return useContext(AlertContext);
}
