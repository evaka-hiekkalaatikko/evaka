// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import PersonSearchPage from 'e2e-playwright/pages/employee/person-search'
import { employeeLogin } from 'e2e-playwright/utils/user'
import config from 'e2e-test-common/config'
import { resetDatabase } from 'e2e-test-common/dev-api'
import { Fixture } from 'e2e-test-common/dev-api/fixtures'
import LocalDate from 'lib-common/local-date'
import { Page } from '../../utils/page'

let page: Page
let personSearchPage: PersonSearchPage

beforeEach(async () => {
  await resetDatabase()
  const admin = await Fixture.employeeAdmin().save()

  page = await Page.open()
  await employeeLogin(page, admin.data)
  await page.goto(`${config.employeeUrl}/search`)
  personSearchPage = new PersonSearchPage(page)
})

describe('Create person', () => {
  test('Create person without a SSN', async () => {
    const person = {
      firstName: 'Etunimi',
      lastName: 'Sukunimi',
      dateOfBirth: LocalDate.today().subDays(30),
      streetAddress: 'Osoite 1',
      postalCode: '02100',
      postOffice: 'Espoo'
    }
    await personSearchPage.createPerson(person)
    await personSearchPage.findPerson(person.firstName)
    await personSearchPage.assertPersonData(person)
  })

  test('Create person without a SSN and then add a SSN', async () => {
    const person = {
      firstName: 'Etunimi',
      lastName: 'Sukunimi',
      dateOfBirth: LocalDate.today().subDays(30),
      streetAddress: 'Osoite 1',
      postalCode: '02100',
      postOffice: 'Espoo'
    }
    await personSearchPage.createPerson(person)
    await personSearchPage.findPerson(person.firstName)
    await personSearchPage.assertPersonData(person)

    // data from mock-vtj-data.json
    const personWithSsn = {
      firstName: 'Ville',
      lastName: 'Vilkas',
      dateOfBirth: LocalDate.parseIso('1999-12-31'),
      streetAddress: 'Toistie 33',
      postalCode: '02230',
      postOffice: 'Espoo',
      ssn: '311299-999E'
    }
    await personSearchPage.addSsn(personWithSsn.ssn)
    await personSearchPage.assertPersonData(personWithSsn)
  })
})
