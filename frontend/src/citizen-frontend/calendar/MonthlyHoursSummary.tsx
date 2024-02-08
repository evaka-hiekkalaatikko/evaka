// SPDX-FileCopyrightText: 2024 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled from 'styled-components'

import LocalDate from 'lib-common/local-date'
import { defaultMargins } from 'lib-components/white-space'

import { useTranslation } from '../localization'

const Title = styled.p`
  font-weight: bold;
  margin-top: 0;
  margin-bottom: ${defaultMargins.xs};
`

const SummaryContainer = styled.div`
  p {
    margin-bottom: 0;
    margin-top: ${defaultMargins.xs};
  }
`

const HoursMinutes = ({ minutes }: { minutes: number }) => {
  const hours = Math.floor(minutes / 60)
  const extraMinutes = minutes % 60

  const i18n = useTranslation()
  return (
    <>
      {hours} {i18n.calendar.monthSummary.hours}
      {!!extraMinutes &&
        ' ' + extraMinutes + ' ' + i18n.calendar.monthSummary.minutes}
    </>
  )
}
export type MonthlyTimeSummary = {
  name: string
  reservedMinutes: number
  serviceNeedMinutes: number
  usedServiceMinutes: number
}

export default React.memo(function MonthSummary({
  year,
  month,
  childSummaries
}: {
  year: number
  month: number
  childSummaries: MonthlyTimeSummary[]
}) {
  const start = LocalDate.of(year, month, 1)
  const end = LocalDate.of(year, month, 1).addMonths(1).subDays(1)
  const i18n = useTranslation()
  return (
    <>
      <Title data-qa="monthly-summary-info-title">
        {i18n.calendar.monthSummary.title}{' '}
        {`${start.format('dd.MM.')} - ${end.format()}`}
      </Title>
      {childSummaries.map((summary, index) => (
        <SummaryContainer key={index} data-qa="monthly-summary-info-text">
          <p>
            <strong>{summary.name}</strong>
          </p>
          <p>
            {i18n.calendar.monthSummary.reserved}{' '}
            {summary.reservedMinutes > summary.serviceNeedMinutes ? (
              <strong>
                <HoursMinutes minutes={summary.reservedMinutes} />
              </strong>
            ) : (
              <HoursMinutes minutes={summary.reservedMinutes} />
            )}{' '}
            / <HoursMinutes minutes={summary.serviceNeedMinutes} />
            <br />
            {i18n.calendar.monthSummary.usedService}{' '}
            {summary.usedServiceMinutes > summary.serviceNeedMinutes ? (
              <strong>
                <HoursMinutes minutes={summary.usedServiceMinutes} />
              </strong>
            ) : (
              <HoursMinutes minutes={summary.usedServiceMinutes} />
            )}{' '}
            / <HoursMinutes minutes={summary.serviceNeedMinutes} />
          </p>
        </SummaryContainer>
      ))}
    </>
  )
})
