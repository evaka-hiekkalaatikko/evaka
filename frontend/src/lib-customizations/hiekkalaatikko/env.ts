// SPDX-FileCopyrightText: 2017-2024 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

export type Env = 'staging' | 'prod'

export const env = (): Env | 'default' => {
  if (window.location.host === 'evaka.example.org') {
    return 'prod'
  }

  if (window.location.host === 'staging.evaka.example.org') {
    return 'staging'
  }

  return 'default'
}
