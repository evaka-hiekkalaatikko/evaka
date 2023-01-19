// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import LocalDate from 'lib-common/local-date'
import LocalTime from 'lib-common/local-time'

import config from '../../config'
import {
  insertApplications,
  resetDatabase,
  upsertMessageAccounts
} from '../../dev-api'
import {
  AreaAndPersonFixtures,
  initializeAreaAndPersonData
} from '../../dev-api/data-init'
import {
  Fixture,
  applicationFixture,
  applicationFixtureId
} from '../../dev-api/fixtures'
import { EmployeeDetail } from '../../dev-api/types'
import CitizenHeader from '../../pages/citizen/citizen-header'
import CitizenMessagesPage from '../../pages/citizen/citizen-messages'
import ApplicationsPage from '../../pages/employee/applications'
import EmployeeNav from '../../pages/employee/employee-nav'
import { Page } from '../../utils/page'
import { employeeLogin, enduserLogin } from '../../utils/user'

let fixtures: AreaAndPersonFixtures
let citizenPage: Page
let staffPage: Page
let serviceWorker: EmployeeDetail

const mockedTime = HelsinkiDateTime.fromLocal(
  LocalDate.of(2022, 11, 8),
  LocalTime.of(10, 49, 0)
)

beforeEach(async () => {
  await resetDatabase()
  fixtures = await initializeAreaAndPersonData()
  serviceWorker = (await Fixture.employeeServiceWorker().save()).data
  await upsertMessageAccounts()
})

async function openCitizenPage(mockedTime: HelsinkiDateTime) {
  citizenPage = await Page.open({ mockedTime: mockedTime.toSystemTzDate() })
  await enduserLogin(citizenPage)
}

async function openStaffPage(mockedTime: HelsinkiDateTime) {
  staffPage = await Page.open({ mockedTime: mockedTime.toSystemTzDate() })
  await employeeLogin(staffPage, serviceWorker)
  await staffPage.goto(config.employeeUrl)
}

describe('Service Worker Messaging', () => {
  describe('Citizen only', () => {
    it('should NOT show the messaging section for a citizen', async () => {
      await openCitizenPage(mockedTime)
      const header = new CitizenHeader(citizenPage)
      await header.assertNoTab('messages')
    })
  })

  describe('Service Worker and citizen', () => {
    beforeEach(async () => {
      const applFixture = applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.enduserGuardianFixture
      )
      await insertApplications([applFixture])
    })

    it('should be possible for service workers to send messages relating to an application', async () => {
      await openStaffPage(mockedTime)
      const applicationsPage = new ApplicationsPage(staffPage)
      await new EmployeeNav(staffPage).applicationsTab.click()
      const applReadView = await applicationsPage
        .applicationRow(applicationFixtureId)
        .openApplication()
      const messagesPage = await applReadView.openMessagesPage()
      const message = {
        title: 'Message to citizen',
        content: 'Hello citizen!'
      }
      await messagesPage.sendNewMessage({
        ...message,
        receiver: fixtures.enduserGuardianFixture.id
      })

      await openCitizenPage(mockedTime.addHours(1))
      const header = new CitizenHeader(citizenPage)
      await header.selectTab('messages')
      const citizenMessagesPage = new CitizenMessagesPage(citizenPage)
      await citizenMessagesPage.openFirstThread()
      await citizenMessagesPage.assertThreadContent(message)
    })
  })
})
