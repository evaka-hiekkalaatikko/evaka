// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { faReply } from '@fortawesome/free-solid-svg-icons'
import { DATE_FORMAT_DATE_TIME, formatDate } from 'lib-common/date'
import {
  Message,
  MessageThread
} from 'lib-common/generated/api-types/messaging'
import { MessageType } from 'lib-common/generated/enums'
import { UUID } from 'lib-common/types'
import { scrollRefIntoView } from 'lib-common/utils/scrolling'
import InlineButton from 'lib-components/atoms/buttons/InlineButton'
import HorizontalLine from 'lib-components/atoms/HorizontalLine'
import { ContentArea } from 'lib-components/layout/Container'
import { FixedSpaceColumn } from 'lib-components/layout/flex-helpers'
import FileDownloadButton from 'lib-components/molecules/FileDownloadButton'
import { MessageReplyEditor } from 'lib-components/molecules/MessageReplyEditor'
import { Bold, H2, InformationText } from 'lib-components/typography'
import { useRecipients } from 'lib-components/utils/useReplyRecipients'
import { defaultMargins, Gap } from 'lib-components/white-space'
import colors from 'lib-customizations/common'
import { faAngleLeft } from 'lib-icons'
import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState
} from 'react'
import styled from 'styled-components'
import { getAttachmentBlob } from '../../api/attachments'
import { useTranslation } from '../../state/i18n'
import { UIContext } from '../../state/ui'
import { MessageContext } from './MessageContext'
import { MessageTypeChip } from './MessageTypeChip'
import { View } from './types-view'

const MessageContainer = styled.div`
  background-color: white;
  padding: ${defaultMargins.L};
  margin-top: ${defaultMargins.s};

  h2 {
    margin: 0;
  }
`

const TitleRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${defaultMargins.xxs};

  & + & {
    margin-top: ${defaultMargins.L};
  }
`

const StickyTitleRow = styled(TitleRow)`
  position: sticky;
  top: 0;
  padding: ${defaultMargins.L};
  background: ${colors.greyscale.white};
  max-height: 100px;
  overflow: auto;

  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: ${defaultMargins.s};
`

const MessageContent = styled.div`
  padding-top: ${defaultMargins.s};
  white-space: pre-line;
`

function SingleMessage({
  message,
  index,
  onAttachmentUnavailable
}: {
  message: Message
  type?: MessageType
  title?: string
  index: number
  onAttachmentUnavailable: () => void
}) {
  return (
    <MessageContainer>
      <TitleRow>
        <Bold>{message.sender.name}</Bold>
        <InformationText>
          {formatDate(message.sentAt, DATE_FORMAT_DATE_TIME)}
        </InformationText>
      </TitleRow>
      <InformationText>
        {(message.recipientNames || message.recipients.map((r) => r.name)).join(
          ', '
        )}
      </InformationText>
      <MessageContent data-qa="message-content" data-index={index}>
        {message.content}
      </MessageContent>
      {message.attachments.length > 0 && (
        <>
          <HorizontalLine slim />
          <FixedSpaceColumn spacing="xs">
            {message.attachments.map((attachment) => (
              <FileDownloadButton
                key={attachment.id}
                file={attachment}
                fileFetchFn={getAttachmentBlob}
                onFileUnavailable={onAttachmentUnavailable}
                icon
                data-qa={'attachment'}
              />
            ))}
          </FixedSpaceColumn>
        </>
      )}
    </MessageContainer>
  )
}

const ThreadContainer = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
`
const ScrollContainer = styled.div`
  overflow-y: auto;
`

const ReplyToThreadButton = styled(InlineButton)`
  padding-left: 28px;
`
const AutoScrollPositionSpan = styled.span<{ top: string }>`
  position: absolute;
  top: ${(p) => p.top};
`

interface Props {
  accountId: UUID
  goBack: () => void
  thread: MessageThread
  view: View
}

export function SingleThreadView({
  accountId,
  goBack,
  thread: { id: threadId, messages, title, type },
  view
}: Props) {
  const { i18n } = useTranslation()
  const { getReplyContent, sendReply, replyState, setReplyContent } =
    useContext(MessageContext)
  const { setErrorMessage } = useContext(UIContext)
  const [replyEditorVisible, setReplyEditorVisible] = useState<boolean>(false)
  const [stickyTitleRowHeight, setStickyTitleRowHeight] = useState<number>(0)
  const stickyTitleRowRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setStickyTitleRowHeight(stickyTitleRowRef.current?.clientHeight || 0)
  }, [setStickyTitleRowHeight, stickyTitleRowRef])

  const replyContent = getReplyContent(threadId)
  const onUpdateContent = useCallback(
    (content) => setReplyContent(threadId, content),
    [setReplyContent, threadId]
  )
  const { recipients, onToggleRecipient } = useRecipients(messages, accountId)

  const autoScrollRef = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    scrollRefIntoView(autoScrollRef)
  }, [messages, replyEditorVisible])

  const onSubmitReply = () =>
    sendReply({
      content: replyContent,
      messageId: messages.slice(-1)[0].id,
      recipientAccountIds: recipients
        .filter((r) => r.selected)
        .map((r) => r.id),
      accountId
    })

  const canReply = type === 'MESSAGE'
  const editorLabels = useMemo(
    () => ({
      add: i18n.common.add,
      message: i18n.messages.messageEditor.message,
      recipients: i18n.messages.messageEditor.receivers,
      send: i18n.messages.messageEditor.send,
      sending: i18n.messages.messageEditor.sending
    }),
    [i18n]
  )

  const onAttachmentUnavailable = useCallback(
    () =>
      setErrorMessage({
        type: 'error',
        resolveLabel: i18n.common.ok,
        title: i18n.fileUpload.download.modalHeader,
        text: i18n.fileUpload.download.modalMessage
      }),
    [i18n, setErrorMessage]
  )

  return (
    <ThreadContainer>
      <ContentArea opaque>
        <InlineButton
          icon={faAngleLeft}
          text={i18n.common.goBack}
          onClick={goBack}
          color={colors.main.primary}
        />
      </ContentArea>
      <Gap size="xs" />
      <ScrollContainer>
        <StickyTitleRow ref={stickyTitleRowRef}>
          <H2 noMargin>{title}</H2>
          <MessageTypeChip type={type} />
        </StickyTitleRow>
        {messages.map((message, idx) => (
          <React.Fragment key={`${message.id}-fragment`}>
            {!replyEditorVisible && idx === messages.length - 1 && (
              <div style={{ position: 'relative' }}>
                <AutoScrollPositionSpan
                  top={`-${stickyTitleRowHeight}px`}
                  ref={autoScrollRef}
                />
              </div>
            )}
            <SingleMessage
              key={message.id}
              message={message}
              index={idx}
              onAttachmentUnavailable={onAttachmentUnavailable}
            />
          </React.Fragment>
        ))}
        {canReply &&
          view == 'RECEIVED' &&
          (replyEditorVisible ? (
            <MessageContainer>
              <MessageReplyEditor
                replyState={replyState}
                onSubmit={onSubmitReply}
                onUpdateContent={onUpdateContent}
                recipients={recipients}
                onToggleRecipient={onToggleRecipient}
                replyContent={replyContent}
                i18n={editorLabels}
              />
            </MessageContainer>
          ) : (
            <>
              <Gap size={'s'} />
              <ReplyToThreadButton
                icon={faReply}
                onClick={() => setReplyEditorVisible(true)}
                data-qa="message-reply-editor-btn"
                text={i18n.messages.replyToThread}
              />
              <Gap size={'m'} />
            </>
          ))}
        {replyEditorVisible && <span ref={autoScrollRef} />}
      </ScrollContainer>
    </ThreadContainer>
  )
}
