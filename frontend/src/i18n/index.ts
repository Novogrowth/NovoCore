import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import elCommon from './locales/el/common.json'
import elEnums from './locales/el/enums.json'
import elNav from './locales/el/nav.json'
import enCommon from './locales/en/common.json'
import enEnums from './locales/en/enums.json'
import enNav from './locales/en/nav.json'

/**
 * English and Greek, decided here.
 *
 * The backend deliberately does not maintain a list of supported languages: `LanguageTag` checks
 * the *shape* of the tag and says in as many words that which languages are offered is the
 * frontend's decision, because Q47(b) settled that the backend localises nothing. This is that
 * decision.
 *
 * The consequence of the same ruling, which no amount of frontend work changes: validation
 * messages from the API arrive as English prose with no code to key from, so a refusal reads in
 * English inside a Greek UI. Translating them needs error codes on the backend.
 */
export const LANGUAGES = ['en', 'el'] as const
export type Language = (typeof LANGUAGES)[number]

export const DEFAULT_LANGUAGE: Language = 'en'

const resources = {
  en: { common: enCommon, nav: enNav, enums: enEnums },
  el: { common: elCommon, nav: elNav, enums: elEnums },
}

/**
 * Narrows whatever `/api/me` returns to a language this application actually has.
 *
 * The backend accepts any well-formed tag, including regions and languages nobody has translated
 * — `el-GR` and `fr` are both storable. `el-GR` is Greek; `fr` is not something we can render, and
 * falling back is the only honest answer.
 */
export function resolveLanguage(tag: string | undefined | null): Language {
  if (!tag) return DEFAULT_LANGUAGE
  const primary = tag.split('-')[0]?.toLowerCase()
  return LANGUAGES.find((language) => language === primary) ?? DEFAULT_LANGUAGE
}

void i18n.use(initReactI18next).init({
  resources,
  lng: DEFAULT_LANGUAGE,
  fallbackLng: DEFAULT_LANGUAGE,
  defaultNS: 'common',
  ns: ['common', 'nav', 'enums'],
  /*
   * Keys are flat, dots and all: `nav.notBuilt` is one key rather than a path into an object, and
   * `inventory.writeOffs` names a nav node whose id contains a dot. Leaving the default separator
   * on would silently turn both into nested lookups that resolve to nothing.
   */
  keySeparator: false,
  interpolation: { escapeValue: false },
})

export default i18n
