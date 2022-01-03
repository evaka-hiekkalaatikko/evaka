// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import {
  careArea2Fixture,
  daycare2Fixture,
  daycareFixture,
  Fixture,
  voucherValueDecisionsFixture
} from 'e2e-test-common/dev-api/fixtures'
import {
  insertVoucherValueDecisionFixtures,
  resetDatabase
} from 'e2e-test-common/dev-api'
import config from 'e2e-test-common/config'
import { initializeAreaAndPersonData } from 'e2e-test-common/dev-api/data-init'
import EmployeeNav from 'e2e-playwright/pages/employee/employee-nav'
import ReportsPage, {
  VoucherServiceProvidersReport
} from 'e2e-playwright/pages/employee/reports'
import assert from 'assert'
import { employeeLogin } from 'e2e-playwright/utils/user'
import { Page } from 'e2e-playwright/utils/page'

let page: Page
let report: VoucherServiceProvidersReport

beforeEach(async () => {
  await resetDatabase()
  const fixtures = await initializeAreaAndPersonData()
  const careArea = await Fixture.careArea().with(careArea2Fixture).save()
  await Fixture.daycare().with(daycare2Fixture).careArea(careArea).save()

  await insertVoucherValueDecisionFixtures([
    voucherValueDecisionsFixture(
      'e2d75fa4-7359-406b-81b8-1703785ca649',
      fixtures.enduserGuardianFixture.id,
      fixtures.enduserChildFixtureKaarina.id,
      fixtures.daycareFixture.id,
      null,
      'SENT',
      '2020-01-01',
      '2020-12-31'
    ),
    voucherValueDecisionsFixture(
      'ed462aca-f74e-4384-910f-628823201023',
      fixtures.enduserGuardianFixture.id,
      fixtures.enduserChildFixtureJari.id,
      daycare2Fixture.id,
      null,
      'SENT',
      '2020-01-01',
      '2020-12-31'
    )
  ])
  const admin = await Fixture.employeeAdmin().save()

  page = await Page.open({ acceptDownloads: true })
  await employeeLogin(page, admin.data)

  await page.goto(config.employeeUrl)
  await new EmployeeNav(page).openTab('reports')

  report = await new ReportsPage(page).openVoucherServiceProvidersReport()
})

describe('Reporting - voucher reports', () => {
  test('voucher service providers are reported correctly, respecting the area filter', async () => {
    await report.selectMonth('Tammikuu')
    await report.selectYear(2020)
    await report.selectArea('Superkeskus')

    await report.assertRowCount(1)
    await report.assertRow(daycareFixture.id, '1', '581,00')

    const csvReport = await report.getCsvReport()
    assert(
      csvReport.includes(daycareFixture.name),
      `Expected csv report to contain ${daycareFixture.name}`
    )
    assert(
      !csvReport.includes(daycare2Fixture.name),
      `Expected csv report to not contain ${daycare2Fixture.name}`
    )
  })
})
