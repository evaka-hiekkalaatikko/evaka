// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Page } from 'e2e-playwright/utils/page'
import { employeeLogin } from 'e2e-playwright/utils/user'
import config from 'e2e-test-common/config'
import {
  insertFeeDecisionFixtures,
  resetDatabase
} from 'e2e-test-common/dev-api'
import { initializeAreaAndPersonData } from 'e2e-test-common/dev-api/data-init'
import {
  daycareFixture,
  enduserChildFixtureKaarina,
  enduserGuardianFixture,
  feeDecisionsFixture,
  Fixture,
  uuidv4
} from 'e2e-test-common/dev-api/fixtures'
import DateRange from 'lib-common/date-range'
import LocalDate from 'lib-common/local-date'
import GuardianInformationPage from '../../pages/employee/guardian-information'

let page: Page
let guardianPage: GuardianInformationPage

beforeEach(async () => {
  await resetDatabase()
  await initializeAreaAndPersonData()
  const financeAdmin = await Fixture.employeeFinanceAdmin().save()

  page = await Page.open({ acceptDownloads: true })
  await employeeLogin(page, financeAdmin.data)

  await page.goto(config.employeeUrl)
  guardianPage = new GuardianInformationPage(page)
})

describe('Head of family fee decisions', () => {
  test('Fee decisions are sorted by sent date', async () => {
    const sentAtFirst = LocalDate.today().subDays(3)
    const sentAtSecond = sentAtFirst.addDays(1)
    const sentAtThird = sentAtSecond.addDays(1)

    const createFeeDecisionFixture = async (sentAt: LocalDate) => {
      await insertFeeDecisionFixtures([
        feeDecisionsFixture(
          'SENT',
          enduserGuardianFixture,
          enduserChildFixtureKaarina,
          daycareFixture.id,
          null,
          new DateRange(sentAt, sentAt),
          sentAt.toSystemTzDate(),
          uuidv4()
        )
      ])
    }

    await createFeeDecisionFixture(sentAtFirst)
    await createFeeDecisionFixture(sentAtThird)
    await createFeeDecisionFixture(sentAtSecond)

    await guardianPage.navigateToGuardian(enduserGuardianFixture.id)
    const feeDecisions = await guardianPage.openCollapsible('feeDecisions')

    await feeDecisions.checkFeeDecisionSentAt(0, sentAtThird)
    await feeDecisions.checkFeeDecisionSentAt(1, sentAtSecond)
    await feeDecisions.checkFeeDecisionSentAt(2, sentAtFirst)
  })
})
