// SPDX-FileCopyrightText: 2017-2024 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import type { EmployeeMobileCustomizations } from 'lib-customizations/types'

import { employeeMobileConfig } from './appConfigs'
import featureFlags from './featureFlags'

const customizations: EmployeeMobileCustomizations = {
  appConfig: employeeMobileConfig,
  featureFlags,
  translations: {
    fi: {}
  }
}

export default customizations
