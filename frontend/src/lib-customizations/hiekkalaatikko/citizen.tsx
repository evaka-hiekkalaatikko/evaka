// SPDX-FileCopyrightText: 2017-2024 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled from 'styled-components'

import { FixedSpaceColumn } from 'lib-components/layout/flex-helpers'
import type { CitizenCustomizations } from 'lib-customizations/types'

import colors from 'lib-customizations/common'

import { citizenConfig } from './appConfigs'
import CommonLogo from './assets/CommonLogoPrimary.svg'
import featureFlags from './featureFlags'
import mapConfig from './mapConfig'

const MultiLineCheckboxLabel = styled(FixedSpaceColumn).attrs({
  spacing: 'zero'
})`
  margin-top: -6px;
`

const customizations: CitizenCustomizations = {
  appConfig: citizenConfig,
  langs: ['fi', 'sv', 'en'],
  translations: {
    fi: {
      footer: {
        cityLabel: 'Evaka',
        privacyPolicyLink: (
          <a
            href="https://www.example.org/"
            data-qa="footer-policy-link"
            style={{ color: colors.main.m2 }}
          >
            Tietosuojaselosteet
          </a>
        ),
        accessibilityStatement: 'Saavutettavuusseloste',
        sendFeedbackLink: (
          <a
            href="https://www.example.org/"
            data-qa="footer-feedback-link"
            style={{ color: colors.main.m2 }}
          >
            Lähetä palautetta
          </a>
        )
      },
      applications: {
        editor: {
          serviceNeed: {
            assistanceNeeded:
              'Lapsella on kehitykseen tai oppimiseen liittyvä tuen tarve'
          },
          verification: {
            serviceNeed: {
              assistanceNeed: {
                assistanceNeed: 'Kehitykseen tai oppimiseen liittyvä tuen tarve'
              }
            }
          }
        }
      },
      income: {
        assure: (
          <MultiLineCheckboxLabel>
            <span>
              Vakuutan antamani tiedot oikeiksi ja olen tutustunut
              asiakasmaksutiedotteeseen:
            </span>
          </MultiLineCheckboxLabel>
        ),
        incomeType: {
          description: (
            <>
              Jos olet yrittäjä, mutta sinulla on myös muita tuloja, valitse
              sekä <strong>Yrittäjän tulotiedot</strong>, että{' '}
              <strong>Asiakasmaksun määritteleminen tulojen mukaan</strong>.
            </>
          ),
          grossIncome: 'Maksun määritteleminen tulojen mukaan'
        },
        grossIncome: {
          title: 'Tulotietojen täyttäminen',
          estimate: 'Arvio palkkatuloistani (ennen veroja)'
        }
      }
    },
    sv: {
      footer: {
        cityLabel: 'Evaka',
        privacyPolicyLink: (
          <a
            href="https://www.example.org/"
            data-qa="footer-policy-link"
            style={{ color: colors.main.m2 }}
          >
            Dataskyddsbeskrivningar
          </a>
        ),
        accessibilityStatement: 'Tillgänglighetsutlåtande',
        sendFeedbackLink: (
          <a
            href="https://www.example.org"
            data-qa="footer-feedback-link"
            style={{ color: colors.main.m2 }}
          >
            Skicka feedback
          </a>
        )
      },
      applications: {
        editor: {
          serviceNeed: {
            assistanceNeeded:
              'Barnet har behov av stöd i anslutning till utveckling eller lärande'
          },
          verification: {
            serviceNeed: {
              assistanceNeed: {
                assistanceNeed:
                  'Behov av stöd i anslutning till utveckling eller lärande'
              }
            }
          }
        }
      },
      income: {
        assure: (
          <MultiLineCheckboxLabel>
            <span>
              Jag försäkrar att de uppgifter jag lämnat in är riktiga och jag
              har bekantat mig med kundcirkuläret gällande avgifter för
              småbarnspedagogik:{' '}
            </span>
          </MultiLineCheckboxLabel>
        ),
        incomeType: {
          description: (
            <>
              Om du är företagare men har också andra inkomster, välj både{' '}
              <strong>Företagarens inkomstuppgifter</strong>, och{' '}
              <strong>Fastställande av klientavgiften enligt inkomster</strong>.
            </>
          ),
          grossIncome: 'Fastställande av avgiften enligt inkomster'
        },
        grossIncome: {
          title: 'Att fylla i uppgifterna om inkomster',
          estimate: 'Uppskattning av mina bruttolön (före skatt)'
        }
      }
    },
    en: {
      footer: {
        cityLabel: 'Evaka',
        privacyPolicyLink: (
          <a
            href="https://www.example.org/"
            data-qa="footer-policy-link"
            style={{ color: colors.main.m2 }}
          >
            Privacy Notices
          </a>
        ),
        accessibilityStatement: 'Accessibility statement',
        sendFeedbackLink: (
          <a
            href="https://www.example.org/"
            data-qa="footer-feedback-link"
            style={{ color: colors.main.m2 }}
          >
            Give feedback
          </a>
        )
      },
      applications: {
        editor: {
          serviceNeed: {
            assistanceNeeded:
              'The child needs support for development or learning'
          },
          verification: {
            serviceNeed: {
              assistanceNeed: {
                assistanceNeed: 'Support for development or learning'
              }
            }
          }
        }
      },
      income: {
        assure: (
          <MultiLineCheckboxLabel>
            <span>
              I confirm that the information I have provided is accurate, and I
              have reviewed the customer fee information:
            </span>
          </MultiLineCheckboxLabel>
        ),
        incomeType: {
          description: (
            <>
              If you are an entrepreneur but also have other income, choose both{' '}
              <strong>Entrepreneur&apos;s income information</strong>, and{' '}
              <strong>Determination of the client fee by income</strong>.
            </>
          ),
          grossIncome: 'Determination of the client fee by income'
        },
        grossIncome: {
          title: 'Filling in income data',
          estimate: 'Estimate of my gross salary (before taxes)'
        }
      }
    }
  },
  cityLogo: {
    src: CommonLogo,
    alt: 'Common Logo'
  },
  routeLinkRootUrl: 'https://reittiopas.hsl.fi/reitti/',
  mapConfig,
  featureFlags,
  getMaxPreferredUnits() {
    return 3
  }
}

export default customizations
