// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { DocumentTemplateContent } from 'lib-common/generated/api-types/document'
import { mutation, query } from 'lib-common/query'
import { UUID } from 'lib-common/types'

import {
  getActiveDocumentTemplateSummaries,
  getDocumentTemplate,
  getDocumentTemplateSummaries,
  postDocumentTemplate,
  putDocumentTemplateContent,
  putDocumentTemplatePublish
} from '../../api/document-templates'
import { createQueryKeys } from '../../query'

const queryKeys = createQueryKeys('documentTemplates', {
  documentTemplateSummaries: () => ['documentTemplateSummaries'],
  documentTemplate: (templateId: UUID) => ['documentTemplates', templateId]
})

export const documentTemplateSummariesQuery = query({
  api: getDocumentTemplateSummaries,
  queryKey: queryKeys.documentTemplateSummaries
})

export const activeDocumentTemplateSummariesQuery = query({
  api: getActiveDocumentTemplateSummaries,
  queryKey: queryKeys.documentTemplateSummaries
})

export const documentTemplateQuery = query({
  api: getDocumentTemplate,
  queryKey: queryKeys.documentTemplate
})

export const createDocumentTemplateMutation = mutation({
  api: postDocumentTemplate,
  invalidateQueryKeys: () => [queryKeys.documentTemplateSummaries()]
})

export const updateDocumentTemplateContentMutation = mutation({
  api: (arg: { id: UUID; content: DocumentTemplateContent }) =>
    putDocumentTemplateContent(arg.id, arg.content),
  invalidateQueryKeys: (arg) => [
    queryKeys.documentTemplateSummaries(),
    queryKeys.documentTemplate(arg.id)
  ]
})

export const publishDocumentTemplateMutation = mutation({
  api: (id: string) => putDocumentTemplatePublish(id),
  invalidateQueryKeys: (id) => [
    queryKeys.documentTemplateSummaries(),
    queryKeys.documentTemplate(id)
  ]
})
