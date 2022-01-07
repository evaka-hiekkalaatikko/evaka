// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowLeft, faTimes } from 'lib-icons'
import React, { useCallback } from 'react'
import styled from 'styled-components'
import { Child as AttendanceChild } from 'lib-common/generated/api-types/attendance'
import IconButton from 'lib-components/atoms/buttons/IconButton'
import { defaultMargins } from 'lib-components/white-space'
import colors from 'lib-customizations/common'

const SearchInputContainer = styled.div`
  height: 60px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: ${defaultMargins.xs};
`

const SearchInput = styled.input<{ background?: string; showClose: boolean }>`
  width: 100%;
  border: none;
  font-size: 1rem;
  background: ${(p) => p.background ?? colors.greyscale.lightest};
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  padding: 0.75rem;
  padding-left: 55px;
  font-size: 17px;
  outline: none;
  margin-left: -38px;
  margin-right: ${(p) => (p.showClose ? '-25px' : '0')};
  color: ${colors.greyscale.darkest};
  height: 100%;

  &::placeholder {
    color: ${colors.greyscale.dark};
  }

  &:focus {
    border-width: 2px;
    border-radius: 2px;
    border-style: solid;
    border-color: ${colors.main.primaryFocus};
    padding-left: 53px;
  }
`

const CustomIcon = styled(FontAwesomeIcon)`
  color: ${colors.greyscale.dark};
  margin: 0 0.5rem;
  position: relative;
  left: 10px;
  font-size: 22px;
  cursor: pointer;
`

const CustomIconButton = styled(IconButton)`
  float: right;
  position: relative;
  color: ${colors.greyscale.medium};
  right: 20px;
`

type FreeTextSearchProps = {
  value: string
  setValue: (s: string) => void
  placeholder: string
  background?: string
  setShowSearch: (show: boolean) => void
  searchResults: AttendanceChild[]
}

export default function FreeTextSearch({
  value,
  setValue,
  placeholder,
  background,
  setShowSearch,
  searchResults
}: FreeTextSearchProps) {
  const clear = useCallback(() => setValue(''), [setValue])

  return (
    <SearchInputContainer>
      <CustomIcon icon={faArrowLeft} onClick={() => setShowSearch(false)} />
      <SearchInput
        placeholder={placeholder}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        data-qa="free-text-search-input"
        background={background}
        showClose={searchResults.length > 1}
      />
      {searchResults.length > 1 && (
        <CustomIconButton icon={faTimes} onClick={clear} size={'m'} />
      )}
    </SearchInputContainer>
  )
}
