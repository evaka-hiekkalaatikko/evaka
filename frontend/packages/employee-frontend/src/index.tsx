// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import 'core-js/stable'
import React from 'react'
import ReactDOM from 'react-dom'
import * as Sentry from '@sentry/browser'
import App from '~App'
import './index.scss'
import { getEnvironment } from '@evaka/lib-common/src/utils/helpers'
import { config } from '@evaka/employee-frontend/src/configs'
import { polyfill as smoothScrollPolyfill } from 'seamless-scroll-polyfill'

// Load Sentry before React to make Sentry's integrations work automatically
Sentry.init({
  enabled: config.sentry.enabled,
  dsn: config.sentry.dsn,
  environment: getEnvironment()
})

// Smooth-scrolling requires polyfilling in Safari, IE and older browsers:
// https://developer.mozilla.org/en-US/docs/Web/API/Window/scrollTo#browser_compatibility
// https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollIntoView#browser_compatibility
smoothScrollPolyfill()

ReactDOM.render(<App />, document.getElementById('app'))
