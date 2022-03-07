// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Failure, Result, Success } from 'lib-common/api'
import {
  deserializeFixedPeriodQuestionnaire,
  deserializeHolidayPeriod
} from 'lib-common/api-types/holiday-period'
import {
  HolidayPeriod,
  FixedPeriodsBody,
  FixedPeriodQuestionnaire
} from 'lib-common/generated/api-types/holidayperiod'
import { JsonOf } from 'lib-common/json'
import { UUID } from 'lib-common/types'

import { client } from '../api-client'

export function getHolidayPeriods(): Promise<Result<HolidayPeriod[]>> {
  return client
    .get<JsonOf<HolidayPeriod[]>>(`/citizen/holiday-period`)
    .then((res) => Success.of(res.data.map(deserializeHolidayPeriod)))
    .catch((e) => Failure.fromError(e))
}

export function getActiveQuestionnaires(): Promise<
  Result<FixedPeriodQuestionnaire[]>
> {
  return client
    .get<JsonOf<FixedPeriodQuestionnaire[]>>(
      `/citizen/holiday-period/questionnaire`
    )
    .then((res) =>
      Success.of(res.data.map(deserializeFixedPeriodQuestionnaire))
    )
    .catch((e) => Failure.fromError(e))
}

export async function postFixedPeriodQuestionnaireAnswer(
  id: UUID,
  request: FixedPeriodsBody
): Promise<Result<void>> {
  return client
    .post(`/citizen/holiday-period/questionnaire/fixed-period/${id}`, request)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}
