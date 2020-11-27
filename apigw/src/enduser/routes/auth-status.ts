// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { NextFunction, Request, Response, Router } from 'express'
import { SessionType } from '../../shared/session'
import { csrf, csrfCookie } from '../../shared/middleware/csrf'
import { getUserDetails } from '../../shared/service-client'

export default function authStatus(sessionType: SessionType) {
  const router = Router()

  router.get(
    '/auth/status',
    csrf,
    csrfCookie(sessionType),
    (req: Request, res: Response, next: NextFunction) => {
      if (req.user) {
        getUserDetails(req, req.user.id)
          .then((data) => res.status(200).send({ loggedIn: true, user: data }))
          .catch((error) => next(error))
      } else {
        res.status(200).send({ loggedIn: false })
      }
    }
  )

  return router
}
