// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { BaseAppConfig } from 'lib-customizations/types'

import { env, Env } from './env'

type AppConfigs = {
  default: BaseAppConfig
} & {
  [k in Env]: BaseAppConfig
}

const employeeConfigs: AppConfigs = {
  default: {
    sentry: {
      dsn: '',
      enabled: false
    }
  },
  staging: {
    sentry: {
      dsn: '',
      enabled: false
    }
  },
  prod: {
    sentry: {
      dsn: '',
      enabled: false
    }
  }
}

const employeeMobileConfigs: AppConfigs = {
  default: {
    sentry: {
      dsn: '',
      enabled: false
    }
  },
  staging: {
    sentry: {
      dsn: '',
      enabled: false
    }
  },
  prod: {
    sentry: {
      dsn: '',
      enabled: false
    }
  }
}

const citizenConfigs: AppConfigs = {
  default: {
    sentry: {
      dsn: '',
      enabled: false
    }
  },
  staging: {
    sentry: {
      dsn: '',
      enabled: false
    }
  },
  prod: {
    sentry: {
      dsn: '',
      enabled: false
    }
  }
}

const employeeConfig = employeeConfigs[env()]
const employeeMobileConfig = employeeMobileConfigs[env()]
const citizenConfig = citizenConfigs[env()]

export { employeeConfig, employeeMobileConfig, citizenConfig }
