// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { IconDefinition } from '@fortawesome/fontawesome-svg-core'
import React from 'react'
import styled, { css } from 'styled-components'

import { faTimes } from 'lib-icons'

import RoundIcon from '../atoms/RoundIcon'
import IconButton from '../atoms/buttons/IconButton'
import { desktopMin, tabletMin } from '../breakpoints'
import { FixedSpaceRow } from '../layout/flex-helpers'
import { modalZIndex } from '../layout/z-helpers'
import { defaultMargins } from '../white-space'

export interface Props {
  icon: IconDefinition
  iconColor: string
  onClick?: () => void
  onClose?: () => void
  offsetTop?: string
  offsetTopDesktop?: string
  children?: React.ReactNode
}

export default React.memo(function Toast({
  icon,
  iconColor,
  onClick,
  onClose,
  offsetTop,
  offsetTopDesktop,
  children
}: Props) {
  return (
    <ToastRoot
      role="dialog"
      offsetTop={offsetTop}
      offsetTopDesktop={offsetTopDesktop}
      showPointer={!!onClick}
    >
      <FixedSpaceRow alignItems="center">
        <RoundIcon
          content={icon}
          color={iconColor}
          size="L"
          onClick={onClick}
        />
        <ToastContent onClick={onClick}>{children}</ToastContent>
        {onClose && (
          <CloseButton icon={faTimes} onClick={onClose} altText="Close" />
        )}
      </FixedSpaceRow>
    </ToastRoot>
  )
})

const ToastRoot = styled.div<{
  offsetTop?: string
  offsetTopDesktop?: string
  showPointer: boolean
}>`
  position: fixed;
  top: ${(p) => p.offsetTop ?? '8px'};
  right: 8px;
  width: 360px;
  @media (min-width: ${tabletMin}) {
    right: 16px;
    width: 480px;
  }
  @media (min-width: ${desktopMin}) {
    ${(p) =>
      p.offsetTopDesktop &&
      css`
        top: ${p.offsetTopDesktop};
      `}
  }
  padding: ${defaultMargins.s};
  background-color: ${(p) => p.theme.colors.grayscale.g0};
  border-radius: 16px;
  box-shadow: 4px 4px 8px rgba(15, 15, 15, 0.15),
    -2px 0 4px rgba(15, 15, 15, 0.15);
  z-index: ${modalZIndex};
  cursor: ${(p) => (p.showPointer ? 'pointer' : 'auto')};
`

const ToastContent = styled.div`
  flex-grow: 1;
`

const CloseButton = styled(IconButton)`
  align-self: flex-start;
  color: ${(p) => p.theme.colors.main.m2};
`
