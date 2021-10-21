// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import { useParams } from 'react-router-dom'
import styled from 'styled-components'
import {
  defaultMargins,
  isSpacingSize,
  SpacingSize
} from 'lib-components/white-space'
import { FixedSpaceColumn } from 'lib-components/layout/flex-helpers'
import ChildListItem from './ChildListItem'
import colors from 'lib-customizations/common'
import { useTranslation } from '../../state/i18n'
import {
  AttendanceStatus,
  Child
} from 'lib-common/generated/api-types/attendance'
import { GroupNote } from 'lib-common/generated/api-types/messaging'

interface Props {
  attendanceChildren: Child[]
  groupsNotes: GroupNote[]
  type?: AttendanceStatus
  showAll?: boolean
}

const NoChildrenOnList = styled.div`
  text-align: center;
  margin-top: 40px;
`

export default React.memo(function AttendanceList({
  attendanceChildren,
  type,
  showAll,
  groupsNotes
}: Props) {
  const { i18n } = useTranslation()

  const { unitId, groupId: groupIdOrAll } = useParams<{
    unitId: string
    groupId: string | 'all'
  }>()

  const filteredChildren = attendanceChildren.filter((ac) => {
    const allowedType = type ? ac.status === type : true
    const allowedGroup =
      groupIdOrAll !== 'all' && !showAll ? ac.groupId === groupIdOrAll : true
    return allowedType && allowedGroup
  })

  const getGroupNote = (child: Child): GroupNote | undefined =>
    groupsNotes.find(({ groupId }) => groupId == child.groupId)

  return (
    <FixedSpaceColumn>
      <OrderedList spacing={'zero'}>
        {filteredChildren.length > 0 ? (
          filteredChildren.map((ac) => (
            <Li key={ac.id}>
              <ChildListItem
                type={type}
                key={ac.id}
                child={ac}
                groupNote={getGroupNote(ac)}
                childAttendanceUrl={`/units/${unitId}/groups/${ac.groupId}/child-attendance/${ac.id}`}
              />
            </Li>
          ))
        ) : (
          <NoChildrenOnList data-qa={'no-children-indicator'}>
            {i18n.mobile.emptyList.no}{' '}
            {i18n.mobile.emptyList.status[type || 'ABSENT']}{' '}
            {i18n.mobile.emptyList.children}
          </NoChildrenOnList>
        )}
      </OrderedList>
    </FixedSpaceColumn>
  )
})

const OrderedList = styled.ol<{ spacing?: SpacingSize | string }>`
  list-style: none;
  padding: 0;
  margin-top: 0;

  li {
    margin-bottom: ${(p) =>
      p.spacing
        ? isSpacingSize(p.spacing)
          ? defaultMargins[p.spacing]
          : p.spacing
        : defaultMargins.s};

    &:last-child {
      margin-bottom: 0;
    }
  }
`

const Li = styled.li`
  &:after {
    content: '';
    width: calc(100% - ${defaultMargins.s});
    background: ${colors.greyscale.lighter};
    height: 1px;
    display: block;
    position: absolute;
    left: ${defaultMargins.s};
  }
`
