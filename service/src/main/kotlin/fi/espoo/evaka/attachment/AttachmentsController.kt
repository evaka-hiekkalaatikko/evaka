// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.attachment

import fi.espoo.evaka.Audit
import fi.espoo.evaka.BucketEnv
import fi.espoo.evaka.EvakaEnv
import fi.espoo.evaka.application.ApplicationStateService
import fi.espoo.evaka.application.utils.exhaust
import fi.espoo.evaka.messaging.findMessageAccountIdByDraftId
import fi.espoo.evaka.messaging.getMessageAccountIdsByContentId
import fi.espoo.evaka.pedagogicaldocument.PedagogicalDocumentNotificationService
import fi.espoo.evaka.s3.ContentTypePattern
import fi.espoo.evaka.s3.DocumentService
import fi.espoo.evaka.s3.checkFileContentTypeAndExtension
import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.AttachmentId
import fi.espoo.evaka.shared.FeeAlterationId
import fi.espoo.evaka.shared.IncomeId
import fi.espoo.evaka.shared.IncomeStatementId
import fi.espoo.evaka.shared.MessageDraftId
import fi.espoo.evaka.shared.PedagogicalDocumentId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.Forbidden
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class AttachmentsController(
    private val documentClient: DocumentService,
    private val stateService: ApplicationStateService,
    private val pedagogicalDocumentNotificationService: PedagogicalDocumentNotificationService,
    private val accessControl: AccessControl,
    private val attachmentsService: AttachmentService,
    evakaEnv: EvakaEnv,
    bucketEnv: BucketEnv
) {
    private val filesBucket = bucketEnv.attachments
    private val maxAttachmentsPerUser = evakaEnv.maxAttachmentsPerUser

    @PostMapping(
        "/attachments/applications/{applicationId}",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadApplicationAttachmentEmployee(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable applicationId: ApplicationId,
        @RequestParam type: AttachmentType,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Application.UPLOAD_ATTACHMENT,
                        applicationId
                    )
                }
                handleFileUpload(
                    dbc,
                    user,
                    clock,
                    AttachmentParent.Application(applicationId),
                    file,
                    type
                ) { tx ->
                    stateService.reCalculateDueDate(tx, clock.today(), applicationId)
                }
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForApplication.log(
                    targetId = applicationId,
                    objectId = attachmentId,
                    meta = mapOf("type" to type.name, "size" to file.size)
                )
            }
    }

    @PostMapping(
        "/attachments/income-statements/{incomeStatementId}",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadIncomeStatementAttachmentEmployee(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable incomeStatementId: IncomeStatementId,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.IncomeStatement.UPLOAD_ATTACHMENT,
                        incomeStatementId
                    )
                }
                handleFileUpload(
                    dbc,
                    user,
                    clock,
                    AttachmentParent.IncomeStatement(incomeStatementId),
                    file
                )
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForIncomeStatement.log(
                    targetId = incomeStatementId,
                    objectId = attachmentId,
                    meta = mapOf("size" to file.size)
                )
            }
    }

    @PostMapping(
        value = ["/attachments/income/{incomeId}", "/attachments/income"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadIncomeAttachment(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable(required = false) incomeId: IncomeId?,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                if (incomeId != null) {
                    dbc.read {
                        accessControl.requirePermissionFor(
                            it,
                            user,
                            clock,
                            Action.Income.UPLOAD_ATTACHMENT,
                            incomeId
                        )
                    }
                }
                val attachTo =
                    if (incomeId != null) AttachmentParent.Income(incomeId)
                    else AttachmentParent.None

                handleFileUpload(dbc, user, clock, attachTo, file)
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForIncome.log(
                    targetId = incomeId,
                    objectId = attachmentId,
                    meta = mapOf("size" to file.size)
                )
            }
    }

    @PostMapping(
        "/attachments/messages/{draftId}",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadMessageAttachment(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable draftId: MessageDraftId,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                dbc.read {
                    val accountId = it.findMessageAccountIdByDraftId(draftId) ?: throw NotFound()
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.MessageAccount.ACCESS,
                        accountId
                    )
                }
                handleFileUpload(dbc, user, clock, AttachmentParent.MessageDraft(draftId), file)
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForMessageDraft.log(
                    targetId = draftId,
                    objectId = attachmentId,
                    meta = mapOf("size" to file.size)
                )
            }
    }

    @PostMapping(
        "/attachments/pedagogical-documents/{documentId}",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadPedagogicalDocumentAttachment(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable documentId: PedagogicalDocumentId,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.PedagogicalDocument.CREATE_ATTACHMENT,
                        documentId
                    )
                }
                handleFileUpload(
                    dbc,
                    user,
                    clock,
                    AttachmentParent.PedagogicalDocument(documentId),
                    file
                ) { tx ->
                    pedagogicalDocumentNotificationService.maybeScheduleEmailNotification(
                        tx,
                        documentId
                    )
                }
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForPedagogicalDocument.log(
                    targetId = documentId,
                    objectId = attachmentId,
                    meta = mapOf("size" to file.size)
                )
            }
    }

    @PostMapping(
        value =
            [
                "/attachments/citizen/applications/{applicationId}", // deprecated
                "/citizen/attachments/applications/{applicationId}",
            ],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadApplicationAttachmentCitizen(
        db: Database,
        user: AuthenticatedUser.Citizen,
        clock: EvakaClock,
        @PathVariable applicationId: ApplicationId,
        @RequestParam type: AttachmentType,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        val attachTo = AttachmentParent.Application(applicationId)
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Citizen.Application.UPLOAD_ATTACHMENT,
                        applicationId
                    )
                }

                handleFileUpload(dbc, user, clock, attachTo, file, type) { tx ->
                    stateService.reCalculateDueDate(tx, clock.today(), applicationId)
                }
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForApplication.log(
                    targetId = applicationId,
                    objectId = attachmentId,
                    meta = mapOf("type" to type.name, "size" to file.size)
                )
            }
    }

    @PostMapping(
        value =
            [
                "/attachments/citizen/income-statements/{incomeStatementId}", // deprecated
                "/attachments/citizen/income-statements", // deprecated
                "/citizen/attachments/income-statements/{incomeStatementId}",
                "/citizen/attachments/income-statements",
            ],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadIncomeStatementAttachmentCitizen(
        db: Database,
        user: AuthenticatedUser.Citizen,
        clock: EvakaClock,
        @PathVariable(required = false) incomeStatementId: IncomeStatementId?,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                if (incomeStatementId != null) {
                    dbc.read {
                        accessControl.requirePermissionFor(
                            it,
                            user,
                            clock,
                            Action.Citizen.IncomeStatement.UPLOAD_ATTACHMENT,
                            incomeStatementId
                        )
                    }
                }
                val attachTo =
                    if (incomeStatementId != null)
                        AttachmentParent.IncomeStatement(incomeStatementId)
                    else AttachmentParent.None

                handleFileUpload(dbc, user, clock, attachTo, file)
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForIncomeStatement.log(
                    targetId = incomeStatementId,
                    objectId = attachmentId,
                    meta = mapOf("size" to file.size)
                )
            }
    }

    @PostMapping(
        value = ["/attachments/fee-alteration/{feeAlterationId}", "/attachments/fee-alteration"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadFeeAlterationAttachment(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable(required = false) feeAlterationId: FeeAlterationId?,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        return db.connect { dbc ->
                if (feeAlterationId != null) {
                    dbc.read {
                        accessControl.requirePermissionFor(
                            it,
                            user,
                            clock,
                            Action.FeeAlteration.UPLOAD_ATTACHMENT,
                            feeAlterationId
                        )
                    }
                }
                val attachTo =
                    if (feeAlterationId != null) AttachmentParent.FeeAlteration(feeAlterationId)
                    else AttachmentParent.None

                handleFileUpload(dbc, user, clock, attachTo, file)
            }
            .also { attachmentId ->
                Audit.AttachmentsUploadForFeeAlteration.log(
                    targetId = feeAlterationId,
                    objectId = attachmentId,
                    meta = mapOf("size" to file.size)
                )
            }
    }

    private fun checkAttachmentCount(
        db: Database.Connection,
        attachTo: AttachmentParent,
        user: AuthenticatedUser.Citizen
    ) {
        val count =
            db.read {
                when (attachTo) {
                    is AttachmentParent.None,
                    is AttachmentParent.Application,
                    is AttachmentParent.IncomeStatement,
                    is AttachmentParent.Income,
                    is AttachmentParent.PedagogicalDocument, ->
                        it.userAttachmentCount(user.evakaUserId, attachTo)
                    is AttachmentParent.MessageDraft,
                    is AttachmentParent.MessageContent -> 0
                    is AttachmentParent.FeeAlteration -> Integer.MAX_VALUE
                }
            }
        if (count >= maxAttachmentsPerUser) {
            throw Forbidden(
                "Too many uploaded files for ${user.evakaUserId}: $maxAttachmentsPerUser"
            )
        }
    }

    private fun getAndCheckFileName(file: MultipartFile) =
        (file.originalFilename?.takeIf { it.isNotBlank() } ?: throw BadRequest("Filename missing"))

    private fun getFileExtension(name: String) =
        name
            .split(".")
            .also {
                if (it.size == 1) throw BadRequest("Missing file extension", "EXTENSION_MISSING")
            }
            .last()

    private fun handleFileUpload(
        dbc: Database.Connection,
        user: AuthenticatedUser,
        clock: EvakaClock,
        attachTo: AttachmentParent,
        file: MultipartFile,
        type: AttachmentType? = null,
        onSuccess: ((tx: Database.Transaction) -> Unit)? = null
    ): AttachmentId {
        if (user is AuthenticatedUser.Citizen) {
            checkAttachmentCount(dbc, attachTo, user)
        }

        val allowedContentTypes =
            when (attachTo) {
                is AttachmentParent.PedagogicalDocument ->
                    pedagogicalDocumentAllowedAttachmentContentTypes
                else -> defaultAllowedAttachmentContentTypes
            }
        val fileName = getAndCheckFileName(file)
        val contentType =
            file.inputStream.use { stream ->
                checkFileContentTypeAndExtension(
                    stream,
                    getFileExtension(fileName),
                    allowedContentTypes
                )
            }

        val id =
            attachmentsService.saveOrphanAttachment(
                dbc,
                user,
                clock,
                fileName = fileName,
                bytes = file.bytes,
                contentType = contentType,
                type = type
            )
        dbc.transaction { tx ->
            tx.associateOrphanAttachments(user.evakaUserId, attachTo, listOf(id))
            onSuccess?.invoke(tx)
        }
        return id
    }

    @GetMapping(
        value =
            [
                "/attachments/{attachmentId}/download/{requestedFilename}", // deprecated
                "/citizen/attachments/{attachmentId}/download/{requestedFilename}"
            ]
    )
    fun getAttachment(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable attachmentId: AttachmentId,
        @PathVariable requestedFilename: String
    ): ResponseEntity<Any> {
        val attachment =
            db.connect { dbc ->
                dbc.read {
                    val attachment =
                        it.getAttachment(attachmentId)
                            ?: throw NotFound("Attachment $attachmentId not found")
                    when (attachment.attachedTo) {
                        is AttachmentParent.Application ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.READ_APPLICATION_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.Income ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.READ_INCOME_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.IncomeStatement ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.READ_INCOME_STATEMENT_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.MessageContent -> {
                            val accountIds =
                                it.getMessageAccountIdsByContentId(
                                    attachment.attachedTo.messageContentId
                                )
                            accessControl.requirePermissionForSomeTarget(
                                it,
                                user,
                                clock,
                                Action.MessageAccount.ACCESS,
                                accountIds
                            )
                        }
                        is AttachmentParent.MessageDraft -> {
                            val accountId =
                                it.findMessageAccountIdByDraftId(attachment.attachedTo.draftId)
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.MessageAccount.ACCESS,
                                accountId!!
                            )
                        }
                        is AttachmentParent.PedagogicalDocument ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.READ_PEDAGOGICAL_DOCUMENT_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.FeeAlteration ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.READ_FEE_ALTERATION_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.None ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.READ_ORPHAN_ATTACHMENT,
                                attachment.id
                            )
                    }.exhaust()
                    attachment
                }
            }

        if (requestedFilename != attachment.name)
            throw BadRequest("Requested file name doesn't match actual file name for $attachmentId")

        return documentClient.responseInline(filesBucket, "$attachmentId", attachment.name).also {
            Audit.AttachmentsRead.log(targetId = attachmentId)
        }
    }

    @DeleteMapping(
        value =
            [
                "/attachments/{attachmentId}",
                "/attachments/citizen/{attachmentId}", // deprecated
                "/citizen/attachments/{attachmentId}",
            ]
    )
    fun deleteAttachmentHandler(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable attachmentId: AttachmentId
    ) {
        db.connect { dbc ->
            dbc.read {
                    val attachment =
                        it.getAttachment(attachmentId)
                            ?: throw NotFound("Attachment $attachmentId not found")
                    when (attachment.attachedTo) {
                        is AttachmentParent.Application ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_APPLICATION_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.Income ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_INCOME_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.IncomeStatement ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_INCOME_STATEMENT_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.MessageDraft -> {
                            val accountId =
                                it.findMessageAccountIdByDraftId(attachment.attachedTo.draftId)
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.MessageAccount.ACCESS,
                                accountId!!
                            )
                        }
                        is AttachmentParent.MessageContent ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_MESSAGE_CONTENT_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.PedagogicalDocument ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_PEDAGOGICAL_DOCUMENT_ATTACHMENT,
                                attachment.id
                            )
                        is AttachmentParent.FeeAlteration ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_FEE_ALTERATION_ATTACHMENTS,
                                attachment.id
                            )
                        is AttachmentParent.None ->
                            accessControl.requirePermissionFor(
                                it,
                                user,
                                clock,
                                Action.Attachment.DELETE_ORPHAN_ATTACHMENT,
                                attachment.id
                            )
                    }.exhaust()
                    attachment
                }
                .also { attachment -> attachmentsService.deleteAttachment(dbc, attachment.id) }
        }
        Audit.AttachmentsDelete.log(targetId = attachmentId)
    }

    private val defaultAllowedAttachmentContentTypes =
        listOf(
            ContentTypePattern.JPEG,
            ContentTypePattern.PNG,
            ContentTypePattern.PDF,
            ContentTypePattern.MSWORD,
            ContentTypePattern.MSWORD_DOCX,
            ContentTypePattern.OPEN_DOCUMENT_TEXT,
            ContentTypePattern.TIKA_MSOFFICE,
            ContentTypePattern.TIKA_OOXML
        )

    private val pedagogicalDocumentAllowedAttachmentContentTypes =
        defaultAllowedAttachmentContentTypes +
            listOf(ContentTypePattern.VIDEO_ANY, ContentTypePattern.AUDIO_ANY)
}
