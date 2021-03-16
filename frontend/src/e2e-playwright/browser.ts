// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import fs from 'fs'
import path from 'path'
import playwright, {
  Browser,
  BrowserContext,
  BrowserContextOptions
} from 'playwright'
import config from 'e2e-test-common/config'

let browser: Browser

beforeAll(async () => {
  browser = await playwright[config.playwright.browser].launch({
    headless: config.playwright.headless
  })
})

afterEach(async () => {
  for (const ctx of browser?.contexts() ?? []) {
    await ctx.close()
  }
})

afterAll(async () => {
  await browser?.close()
})

const injected = fs.readFileSync(
  path.resolve(__dirname, '../e2e-test-common/injected.js'),
  'utf-8'
)

export async function newBrowserContext(
  options?: BrowserContextOptions
): Promise<BrowserContext> {
  const ctx = await browser.newContext(options)
  await ctx.addInitScript({ content: injected })
  return ctx
}
