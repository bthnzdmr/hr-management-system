import { useEffect } from 'react';
import type { RefObject } from 'react';

/** Yazi girilen alanlar: burada kisayol calisirsa kullanicinin yazisi bozulur. */
const TYPING_TAGS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;

  return TYPING_TAGS.has(target.tagName) || target.isContentEditable;
}

/**
 * "/" ile arama kutusuna odaklanma, "Escape" ile temizleme.
 *
 * Kisayolun asil zorlugu tusa basmak degil, NE ZAMAN basilmadigini bilmektir:
 * kullanici bir metin alanina "/" yaziyorsa kisayol devreye girmemeli. Bu
 * klasik bir hatadir ve fark edilmesi zordur -- yalnizca egik cizgi iceren
 * bir sey yazan kullanici sikayet eder.
 *
 * Degistirici tuslar da disarida: Ctrl+/ veya Cmd+/ tarayicinin ya da isletim
 * sisteminin kisayolu olabilir, onu calmayiz.
 */
export function useSearchShortcut(
  inputRef: RefObject<HTMLInputElement | null>,
  onClear: () => void,
) {
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;

      if (event.key === '/' && !isTyping(event.target)) {
        // preventDefault sart: engellenmezse odaklanma sonrasi "/" karakteri
        // kutuya yazilir ve kullanici her kisayolda bir egik cizgi siler.
        event.preventDefault();
        inputRef.current?.focus();
        return;
      }

      // Escape yalnizca arama kutusundayken is yapar: sayfanin herhangi bir
      // yerinde Escape'e basmak aramayi silmemeli, ve acik bir pencere varsa
      // Escape once onu kapatmali.
      if (event.key === 'Escape' && event.target === inputRef.current) {
        onClear();
      }
    };

    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [inputRef, onClear]);
}
