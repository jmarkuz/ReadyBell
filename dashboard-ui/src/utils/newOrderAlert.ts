/** Sound and vibration for a new order. Everything here is best effort: a browser may refuse any of it. */

export const ALERT_SOUND_STORAGE_KEY = 'readybell-alert-sound';

export function readSoundPreference(): boolean {
  try {
    return localStorage.getItem(ALERT_SOUND_STORAGE_KEY) === 'on';
  } catch {
    return false;
  }
}

export function writeSoundPreference(on: boolean): void {
  try {
    localStorage.setItem(ALERT_SOUND_STORAGE_KEY, on ? 'on' : 'off');
  } catch {
    // private mode or blocked storage: the choice just lasts for this page
  }
}

let audio: AudioContext | null = null;

function audioContext(): AudioContext | null {
  if (audio) return audio;
  const Ctor: typeof AudioContext | undefined =
    typeof window === 'undefined' ? undefined : window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  try {
    audio = new Ctor();
  } catch {
    audio = null;
  }
  return audio;
}

/**
 * Browsers keep audio locked until the user has interacted with the page. Call this from a click or key handler (or
 * once, on the first interaction) so later alerts can be heard.
 */
export function unlockAudio(): void {
  const ctx = audioContext();
  if (ctx && ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
}

/** A short two-tone chime (no audio file to ship). Silent if the browser still blocks audio. */
export function playNewOrderSound(): void {
  const ctx = audioContext();
  if (!ctx) return;
  if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
  const start = ctx.currentTime;
  [[880, 0], [1175, 0.16]].forEach(([frequency, offset]) => {
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(0.0001, start + offset);
    gain.gain.exponentialRampToValueAtTime(0.3, start + offset + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + offset + 0.3);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(start + offset);
    oscillator.stop(start + offset + 0.32);
  });
}

export function vibrateForNewOrder(): void {
  try {
    navigator.vibrate?.([200, 100, 200]);
  } catch {
    // not supported or not allowed without a user gesture
  }
}
