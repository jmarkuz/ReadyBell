/** Strings of the customer tracking page. Ukrainian by default for uk-* browsers, English otherwise. */
const dictionary = {
  en: {
    loading: 'Loading your order…',
    invalidLink: 'This link is not valid',
    invalidLinkHint: 'Ask the staff to show you the QR code from your receipt again.',
    loadError: 'Could not load your order. Retrying…',
    noOrders: 'No orders yet',
    orderLabel: 'Order',
    waitAbout: (m: number) => (m <= 0 ? 'Almost ready' : `about ${m} min`),
    status: {
      CREATED: 'Received',
      IN_PROGRESS: 'Being prepared',
      READY: 'Ready for pickup!',
      COMPLETED: 'Picked up',
      CANCELLED: 'Cancelled',
    } as Record<string, string>,
    notifyTitle: 'Get notified when it is ready',
    notifyHint: 'We will send one notification. No account needed.',
    notifyButton: 'Notify me',
    notifyOn: 'Notifications are on. You can close this page.',
    notifyDenied: 'Notifications are blocked in your browser settings.',
    notifyUnsupported: 'This browser cannot show notifications. Keep this page open instead.',
    notifyError: 'Could not turn notifications on. Please try again.',
    iosHint: 'On iPhone: tap Share → "Add to Home Screen", open the app from there, then tap "Notify me".',
    readyBanner: 'Your order is ready for pickup!',
    live: 'Live',
    offline: 'Reconnecting…',
  },
  uk: {
    loading: 'Завантажуємо ваше замовлення…',
    invalidLink: 'Це посилання недійсне',
    invalidLinkHint: 'Попросіть персонал ще раз показати QR-код з вашого чека.',
    loadError: 'Не вдалося завантажити замовлення. Пробуємо ще раз…',
    noOrders: 'Замовлень ще немає',
    orderLabel: 'Замовлення',
    waitAbout: (m: number) => (m <= 0 ? 'Майже готово' : `приблизно ${m} хв`),
    status: {
      CREATED: 'Прийнято',
      IN_PROGRESS: 'Готується',
      READY: 'Готово до видачі!',
      COMPLETED: 'Видано',
      CANCELLED: 'Скасовано',
    } as Record<string, string>,
    notifyTitle: 'Сповістити, коли буде готово',
    notifyHint: 'Ми надішлемо одне сповіщення. Реєстрація не потрібна.',
    notifyButton: 'Сповістити мене',
    notifyOn: 'Сповіщення увімкнено. Можете закрити цю сторінку.',
    notifyDenied: 'Сповіщення заблоковано в налаштуваннях браузера.',
    notifyUnsupported: 'Цей браузер не показує сповіщень. Залиште цю сторінку відкритою.',
    notifyError: 'Не вдалося увімкнути сповіщення. Спробуйте ще раз.',
    iosHint: 'На iPhone: «Поділитися» → «На екран “Додому”», відкрийте застосунок звідти й натисніть «Сповістити мене».',
    readyBanner: 'Ваше замовлення готове до видачі!',
    live: 'На зв’язку',
    offline: 'Відновлюємо зв’язок…',
  },
};

export type CustomerStrings = typeof dictionary.en;

export function customerStrings(language: string = navigator.language): CustomerStrings {
  return language.toLowerCase().startsWith('uk') ? dictionary.uk : dictionary.en;
}
