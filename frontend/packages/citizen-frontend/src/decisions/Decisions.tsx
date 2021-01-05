// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useEffect, useState } from 'react'
import styled from 'styled-components'
import { ApplicationDecisions, DecisionType } from '~decisions/types'
import { client } from '~api-client'
import { faCheck, faFileAlt, faGavel, faTimes } from '@evaka/lib-icons'
import { JsonOf } from '@evaka/lib-common/src/json'
import LocalDate from '@evaka/lib-common/src/local-date'
import { accentColors, greyscale } from '@evaka/lib-components/src/colors'
import Container, {
  ContentArea
} from '@evaka/lib-components/src/layout/Container'
import { H1, H2, H3, Label, P } from '@evaka/lib-components/src/typography'
import ListGrid from '@evaka/lib-components/src/layout/ListGrid'
import { AlertBox } from '@evaka/lib-components/src/molecules/MessageBoxes'
import { Gap, defaultMargins } from '@evaka/lib-components/src/white-space'
import Link from '@evaka/lib-components/src/atoms/Link'
import Button from '@evaka/lib-components/src/atoms/buttons/Button'
import InlineButton from '@evaka/lib-components/src/atoms/buttons/InlineButton'
import RoundIcon from '@evaka/lib-components/src/atoms/RoundIcon'
import { useTranslation } from '../localization'

const getDecisions = async (): Promise<ApplicationDecisions[]> => {
  const { data } = await client.get<JsonOf<ApplicationDecisions[]>>(
    '/citizen/decisions'
  )
  return data.map(({ applicationId, childName, decisions }) => ({
    applicationId,
    childName,
    decisions: decisions.map((json) => ({
      ...json,
      sentDate: LocalDate.parseIso(json.sentDate),
      resolved: LocalDate.parseNullableIso(json.resolved)
    }))
  }))
}

export default React.memo(function Decisions() {
  const t = useTranslation()
  const [applicationDecisions, setApplicationDecisions] = useState<
    ApplicationDecisions[]
  >([])

  useEffect(() => {
    void getDecisions().then(setApplicationDecisions)
  }, [])

  const unconfirmedDecisionsCount = applicationDecisions.reduce(
    (sum, { decisions }) =>
      sum + decisions.filter(({ status }) => status === 'PENDING').length,
    0
  )

  return (
    <Container>
      <Gap size="s" />
      <ContentArea opaque paddingVertical="L">
        <H1 noMargin>{t.decisions.title}</H1>
        <P
          width="800px"
          dangerouslySetInnerHTML={{ __html: t.decisions.summary }}
        />
        {unconfirmedDecisionsCount > 0 ? (
          <>
            <Gap size="s" />
            <AlertBox
              message={t.decisions.unconfimedDecisions(
                applicationDecisions.length
              )}
              thin
              dataQa="alert-box-unconfirmed-decisions-count"
            />
          </>
        ) : null}
      </ContentArea>
      <Gap size="s" />
      {applicationDecisions.map((applicationDecision) => (
        <React.Fragment key={applicationDecision.applicationId}>
          <ApplicationDecisions {...applicationDecision} />
          <Gap size="s" />
        </React.Fragment>
      ))}
    </Container>
  )
})

const ApplicationDecisions = React.memo(function ApplicationDecisions({
  applicationId,
  childName,
  decisions
}: ApplicationDecisions) {
  const t = useTranslation()

  return (
    <ContentArea
      opaque
      paddingVertical="L"
      data-qa={`application-${applicationId}`}
    >
      <H2 noMargin data-qa={`title-decision-child-name-${applicationId}`}>
        {childName}
      </H2>
      {decisions.map(({ decisionId, type, status, sentDate, resolved }) => (
        <React.Fragment key={decisionId}>
          <Gap size="L" />
          <H3 noMargin data-qa={`title-decision-type-${decisionId}`}>
            {`${t.decisions.applicationDecisions.decision} ${t.decisions.applicationDecisions.type[type]}`}
          </H3>
          <Gap size="m" />
          <MobileFriendlyListGrid
            labelWidth="max-content"
            rowGap="s"
            columnGap="L"
          >
            <Label>{t.decisions.applicationDecisions.sentDate}</Label>
            <span data-qa={`decision-sent-date-${decisionId}`}>
              {sentDate.format()}
            </span>
            {resolved ? (
              <>
                <Label>{t.decisions.applicationDecisions.resolved}</Label>
                <span data-qa={`decision-resolved-date-${decisionId}`}>
                  {resolved.format()}
                </span>
              </>
            ) : null}
            <Label>{t.decisions.applicationDecisions.statusLabel}</Label>
            <Status data-qa={`decision-status-${decisionId}`}>
              <RoundIcon
                content={statusIcon[status].icon}
                color={statusIcon[status].color}
                size="s"
              />
              <Gap size="xs" horizontal />
              {t.decisions.applicationDecisions.status[status]}
            </Status>
          </MobileFriendlyListGrid>
          <Gap size="m" />
          {status === 'PENDING' ? (
            <ConfirmationDialog applicationId={applicationId} type={type} />
          ) : (
            <PdfLink decisionId={decisionId} />
          )}
        </React.Fragment>
      ))}
    </ContentArea>
  )
})

const MobileFriendlyListGrid = styled(ListGrid)`
  @media (max-width: 600px) {
    grid-template-columns: auto;
    row-gap: ${defaultMargins.xxs};

    *:nth-child(2n) {
      margin-bottom: ${defaultMargins.s};
    }
  }
`

const Status = styled.span`
  text-transform: uppercase;
`

const statusIcon = {
  PENDING: {
    icon: faGavel,
    color: accentColors.orange
  },
  ACCEPTED: {
    icon: faCheck,
    color: accentColors.green
  },
  REJECTED: {
    icon: faTimes,
    color: greyscale.lighter
  }
}

const noop = () => undefined

const preschoolInfoTypes: DecisionType[] = [
  'PRESCHOOL',
  'PRESCHOOL_DAYCARE',
  'PREPARATORY_EDUCATION'
]

const ConfirmationDialog = React.memo(function ConfirmationDialog({
  applicationId,
  type
}: {
  applicationId: string
  type: DecisionType
}) {
  const t = useTranslation()

  return (
    <>
      <P width="800px">
        {
          t.decisions.applicationDecisions.confirmationInfo[
            preschoolInfoTypes.includes(type) ? 'preschool' : 'default'
          ]
        }
      </P>
      <P width="800px">
        <strong>{t.decisions.applicationDecisions.goToConfirmation}</strong>
      </P>
      <Gap size="s" />
      <Link to={`/decisions/by-application/${applicationId}`}>
        <Button
          primary
          text={t.decisions.applicationDecisions.confirmationLink}
          onClick={noop}
          dataQa="button-confirm-decisions"
        />
      </Link>
    </>
  )
})

const PdfLink = React.memo(function PdfLink({
  decisionId
}: {
  decisionId: string
}) {
  const t = useTranslation()

  return (
    <a
      href={`/api/application/citizen/decisions/${decisionId}/download`}
      target="_blank"
      rel="noreferrer"
    >
      <InlineButton
        icon={faFileAlt}
        text={t.decisions.applicationDecisions.openPdf}
        onClick={noop}
        dataQa="button-open-pdf"
      />
    </a>
  )
})
