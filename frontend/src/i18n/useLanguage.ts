import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { meControllerChangeLanguage } from '@/api/generated/endpoints/me/me'
import { ME_QUERY_KEY, useSession } from '@/auth/session'

import { resolveLanguage, type Language } from './index'

/**
 * Language is a property of the user, not of the browser.
 *
 * It is stored on the account (`PATCH /api/me/language`, migration V27) so that signing in on the
 * shop's other machine does not mean setting it again. The browser's own preference is only ever
 * a starting point for someone who has never chosen.
 *
 * Called through the generated client rather than by writing the path here: a URL literal outside
 * `src/api/generated/` is a path that a regeneration cannot update, and would keep pointing at a
 * route that had moved.
 */

async function saveLanguage(language: Language): Promise<void> {
  await meControllerChangeLanguage({ language })
}

/** Applies the signed-in user's stored language, and re-applies it when the user changes. */
export function useLanguageSync(): void {
  const { me } = useSession()
  const { i18n } = useTranslation()

  useEffect(() => {
    const language = resolveLanguage(me?.language)
    if (i18n.language !== language) {
      void i18n.changeLanguage(language)
    }
    // The document has to agree, or a screen reader announces Greek text as English and hyphenation
    // rules are wrong.
    document.documentElement.lang = language
  }, [me?.language, i18n])
}

/**
 * Changes it, on the account.
 *
 * The UI follows the server rather than leading it: the language changes once the write has been
 * accepted. `LanguageTag` normalises what it stores — `el-GR`, `EL-GR` and `el-gr` are one
 * preference — so the value that comes back is not always the value that went out, and switching
 * first would mean switching twice.
 */
export function useChangeLanguage() {
  const queryClient = useQueryClient()
  const { i18n } = useTranslation()

  return useMutation({
    mutationFn: (language: Language) => saveLanguage(language),
    onSuccess: async (_result, language) => {
      await i18n.changeLanguage(language)
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
    },
  })
}
