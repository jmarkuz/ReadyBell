/**
 * Strings of the staff panel and the customer display. English is the source (the tests read it), Ukrainian is shown to
 * browsers that ask for it, or when staff pick it with {@link setStaffLanguage}. Screens that are not translated yet keep
 * their English text.
 */
const LANGUAGE_KEY = 'readybell-staff-language';

export type StaffLanguage = 'en' | 'uk';

const en = {
  navDashboard: 'Dashboard',
  navStaff: 'Staff',
  navSettings: 'Settings',
  navRestaurants: 'Restaurants',
  panelName: 'Restaurant Panel',
  logout: 'Logout',
  languageSwitch: 'Українська',
  languageSwitchLabel: 'Switch to Ukrainian',

  dashboardWelcome: 'Welcome to the ReadyBell restaurant dashboard. Monitor incoming orders and track operations.',
  customerDisplay: 'Customer display',
  newOrder: 'New order',
  live: 'Live',
  offline: 'Offline',
  orderSummary: 'Order Summary',
  filters: 'Filters',
  dateRange: 'Date Range',
  activeFilters: 'Active Filters:',
  loadingOrders: 'Loading orders…',
  incomingOrders: 'Incoming Orders',

  statusCreated: 'New Order',
  statusPreparing: 'Preparing',
  statusReady: 'Ready for Pickup',
  statusCompleted: 'Completed',
  statusCancelled: 'Cancelled',
  metricTotal: 'Total Orders',
  metricNew: 'New Orders',

  colOrderId: 'Order ID',
  colCustomer: 'Customer',
  colItems: 'Items',
  colEta: 'ETA',
  colStatus: 'Status',
  colActions: 'Actions',
  noOrders: 'No orders found.',
  connected: 'Connected',
  connectedHint: "The customer's phone is connected to this order",
  showQrTitle: 'Show QR code',
  actionNew: 'Mark as new',
  actionPreparing: 'Start preparing',
  actionReady: 'Mark ready for pickup',
  actionCompleted: 'Mark completed',
  actionCancel: 'Cancel order',
  noFurtherChanges: 'No further status changes',
  updateStatus: 'Update Status',
  pageOf: (page: number, pages: number, total: number) => `Page ${page} of ${pages} (${total} total orders)`,
  previous: 'Previous',
  next: 'Next',

  newOrderTitle: 'New order',
  orderCreatedTitle: 'Order created',
  customerName: 'Customer name',
  optional: '(optional)',
  customerNameHint: 'A first name or initials is enough. It is removed from the order a few weeks after pick-up.',
  items: 'Items',
  itemPlaceholder: 'e.g. Latte',
  addItem: 'Add item',
  readyInMinutes: 'Ready in (minutes)',
  createOrder: 'Create order',
  creating: 'Creating…',
  close: 'Close',

  orderQrTitle: 'Order QR code',
  preparingCode: 'Preparing the code…',
  customerConnected: 'Customer connected',
  customerConnectedHint: 'The phone is following this order. The QR code is hidden.',
  showQrAgain: 'Show QR code again',
  qrHint: 'Show or print this code. The customer scans it to follow the order and get notified.',
  print: 'Print',
  copyLink: 'Copy link',
  copied: 'Copied',

  displayConnected: 'You are connected',
  displayNotify: 'We will notify you when your order is ready.',
  displayScan: 'Scan with your phone to follow your order',
  displayWelcome: 'Welcome! Your order will appear here.',

  loginRestaurant: 'Restaurant',
  loginRestaurantHint: "The restaurant's code, as given to you. Leave empty only for the platform operator.",
  loginUsername: 'Username',
  loginPassword: 'Password',
  loginSignIn: 'Sign in',
  loginSigningIn: 'Signing in…',
  loginInvalid: 'Invalid restaurant, username or password.',
  loginThrottled: 'Too many failed attempts. Please wait a few minutes and try again.',
  loginFailed: 'Sign-in failed. Check your connection and try again.',

  settingsTitle: 'Settings',
  settingsIntro: "Your restaurant's public details.",
  settingsRestaurant: 'Restaurant',
  settingsName: 'Name shown to customers',
  settingsIdentifier: 'Identifier',
  save: 'Save',
  saving: 'Saving…',
  saved: 'Saved.',
  couldNotSave: 'Could not save.',
  staffTitle: 'Staff',
  staffIntro: "Accounts that can sign in to this restaurant's panel.",
  accounts: 'Accounts',
  loading: 'Loading…',
  username: 'Username',
  role: 'Role',
  status: 'Status',
  roleAdmin: 'Admin',
  roleStaff: 'Staff',
  active: 'Active',
  deactivated: 'Deactivated',
  addAccount: 'Add an account',
  addAccountButton: 'Add account',
  passwordMin: 'Password (min. 10 characters)',
  roleStaffOption: 'Staff (orders)',
  roleAdminOption: 'Admin (orders, staff, settings)',
  deactivate: 'Deactivate',
  couldNotLoadStaff: 'Could not load staff.',
  couldNotCreate: 'Could not create the account.',
  couldNotDeactivate: 'Could not deactivate the account.',
  adding: 'Adding…',
};

export type StaffStrings = typeof en;

const uk: StaffStrings = {
  navDashboard: 'Панель',
  navStaff: 'Персонал',
  navSettings: 'Налаштування',
  navRestaurants: 'Ресторани',
  panelName: 'Панель ресторану',
  logout: 'Вийти',
  languageSwitch: 'English',
  languageSwitchLabel: 'Перемкнути на англійську',

  dashboardWelcome: 'Ласкаво просимо до панелі ресторану ReadyBell. Стежте за замовленнями та роботою закладу.',
  customerDisplay: 'Екран для клієнта',
  newOrder: 'Нове замовлення',
  live: 'На зв’язку',
  offline: 'Немає зв’язку',
  orderSummary: 'Огляд замовлень',
  filters: 'Фільтри',
  dateRange: 'Період',
  activeFilters: 'Активні фільтри:',
  loadingOrders: 'Завантажуємо замовлення…',
  incomingOrders: 'Вхідні замовлення',

  statusCreated: 'Нове',
  statusPreparing: 'Готується',
  statusReady: 'Готове до видачі',
  statusCompleted: 'Видане',
  statusCancelled: 'Скасоване',
  metricTotal: 'Усього замовлень',
  metricNew: 'Нові замовлення',

  colOrderId: 'Номер',
  colCustomer: 'Клієнт',
  colItems: 'Позиції',
  colEta: 'Орієнтовно',
  colStatus: 'Статус',
  colActions: 'Дії',
  noOrders: 'Замовлень не знайдено.',
  connected: 'Підключено',
  connectedHint: 'Телефон клієнта підключений до цього замовлення',
  showQrTitle: 'Показати QR-код',
  actionNew: 'Позначити як нове',
  actionPreparing: 'Почати готувати',
  actionReady: 'Готове до видачі',
  actionCompleted: 'Видано',
  actionCancel: 'Скасувати замовлення',
  noFurtherChanges: 'Подальших змін статусу немає',
  updateStatus: 'Змінити статус',
  pageOf: (page: number, pages: number, total: number) => `Сторінка ${page} з ${pages} (усього замовлень: ${total})`,
  previous: 'Назад',
  next: 'Далі',

  newOrderTitle: 'Нове замовлення',
  orderCreatedTitle: 'Замовлення створено',
  customerName: 'Ім’я клієнта',
  optional: '(необов’язково)',
  customerNameHint: 'Достатньо імені або ініціалів. Воно видаляється із замовлення через кілька тижнів після видачі.',
  items: 'Позиції',
  itemPlaceholder: 'наприклад, Лате',
  addItem: 'Додати позицію',
  readyInMinutes: 'Буде готове через (хв)',
  createOrder: 'Створити замовлення',
  creating: 'Створюємо…',
  close: 'Закрити',

  orderQrTitle: 'QR-код замовлення',
  preparingCode: 'Готуємо код…',
  customerConnected: 'Клієнта підключено',
  customerConnectedHint: 'Телефон стежить за цим замовленням. QR-код приховано.',
  showQrAgain: 'Показати QR-код знову',
  qrHint: 'Покажіть або роздрукуйте цей код. Клієнт сканує його, щоб стежити за замовленням і отримати сповіщення.',
  print: 'Друк',
  copyLink: 'Копіювати посилання',
  copied: 'Скопійовано',

  displayConnected: 'Ви підключені',
  displayNotify: 'Ми сповістимо вас, коли замовлення буде готове.',
  displayScan: 'Скануйте телефоном, щоб стежити за замовленням',
  displayWelcome: 'Ласкаво просимо! Тут з’явиться ваше замовлення.',

  loginRestaurant: 'Ресторан',
  loginRestaurantHint: 'Код ресторану, який вам видали. Порожнім лишайте лише оператор платформи.',
  loginUsername: 'Ім’я користувача',
  loginPassword: 'Пароль',
  loginSignIn: 'Увійти',
  loginSigningIn: 'Входимо…',
  loginInvalid: 'Неправильний ресторан, ім’я користувача або пароль.',
  loginThrottled: 'Забагато невдалих спроб. Зачекайте кілька хвилин і спробуйте ще раз.',
  loginFailed: 'Не вдалося увійти. Перевірте з’єднання й спробуйте ще раз.',

  settingsTitle: 'Налаштування',
  settingsIntro: 'Публічні дані вашого ресторану.',
  settingsRestaurant: 'Ресторан',
  settingsName: 'Назва, яку бачать клієнти',
  settingsIdentifier: 'Код',
  save: 'Зберегти',
  saving: 'Зберігаємо…',
  saved: 'Збережено.',
  couldNotSave: 'Не вдалося зберегти.',
  staffTitle: 'Персонал',
  staffIntro: 'Акаунти, які можуть входити в панель цього ресторану.',
  accounts: 'Акаунти',
  loading: 'Завантажуємо…',
  username: 'Ім’я користувача',
  role: 'Роль',
  status: 'Статус',
  roleAdmin: 'Адмін',
  roleStaff: 'Персонал',
  active: 'Активний',
  deactivated: 'Деактивований',
  addAccount: 'Додати акаунт',
  addAccountButton: 'Додати акаунт',
  passwordMin: 'Пароль (щонайменше 10 символів)',
  roleStaffOption: 'Персонал (замовлення)',
  roleAdminOption: 'Адмін (замовлення, персонал, налаштування)',
  deactivate: 'Деактивувати',
  couldNotLoadStaff: 'Не вдалося завантажити персонал.',
  couldNotCreate: 'Не вдалося створити акаунт.',
  couldNotDeactivate: 'Не вдалося деактивувати акаунт.',
  adding: 'Додаємо…',
};

function safeStorage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** The language of the staff panel: the one staff picked, else the browser's, else English. */
export function staffLanguage(): StaffLanguage {
  const chosen = safeStorage()?.getItem(LANGUAGE_KEY);
  if (chosen === 'uk' || chosen === 'en') return chosen;
  return (typeof navigator !== 'undefined' ? navigator.language : 'en').toLowerCase().startsWith('uk') ? 'uk' : 'en';
}

/** Remembers the choice and reloads, so every screen picks it up at once. */
export function setStaffLanguage(language: StaffLanguage) {
  safeStorage()?.setItem(LANGUAGE_KEY, language);
  window.location.reload();
}

export function staffStrings(language: StaffLanguage = staffLanguage()): StaffStrings {
  return language === 'uk' ? uk : en;
}
