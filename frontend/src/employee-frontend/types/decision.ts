// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import LocalDate from 'lib-common/local-date'
import { UUID } from 'lib-common/types'

export type DecisionType =
  | 'CLUB'
  | 'DAYCARE'
  | 'DAYCARE_PART_TIME'
  | 'PRESCHOOL'
  | 'PRESCHOOL_DAYCARE'
  | 'PREPARATORY_EDUCATION'

export interface DecisionDraft {
  id: UUID
  unitId: string
  type: DecisionType
  startDate: LocalDate
  endDate: LocalDate
  planned: boolean
}

export interface DecisionDraftUpdate {
  id: UUID
  unitId: string
  startDate: LocalDate
  endDate: LocalDate
  planned: boolean
}

export interface DecisionDraftGroup {
  decisions: DecisionDraft[]
  placementUnitName: string
  unit: DecisionUnit
  guardian: PersonData
  otherGuardian: PersonData | null
  child: PersonData
}

export interface PersonData {
  id: UUID | null
  ssn: string | null
  firstName: string
  lastName: string
  isVtjGuardian: boolean | null
}

export type DecisionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export interface Decision {
  id: UUID
  type: DecisionType
  startDate: LocalDate
  endDate: LocalDate
  unit: DecisionUnit
  applicationId: UUID
  childId: UUID
  childName: string
  documentKey: string | null
  decisionNumber: number
  sentDate: LocalDate
  status: DecisionStatus
}

export interface DecisionUnit {
  id: UUID
  name: string
  daycareDecisionName: string
  preschoolDecisionName: string
  manager: string | null
  streetAddress: string
  postalCode: string
  postOffice: string
  approverName: string
  decisionHandler: string
  decisionHandlerAddress: string
}
