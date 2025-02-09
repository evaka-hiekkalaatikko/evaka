// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.vasu

import fi.espoo.evaka.Audit
import fi.espoo.evaka.shared.VasuTemplateId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.Conflict
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.FiniteDateRange
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/vasu/templates")
class VasuTemplateController(private val accessControl: AccessControl) {
    data class CreateTemplateRequest(
        val name: String,
        val valid: FiniteDateRange,
        val type: CurriculumType,
        val language: VasuLanguage
    )

    @PostMapping
    fun postTemplate(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestBody body: CreateTemplateRequest
    ): VasuTemplateId {
        return db.connect { dbc ->
                dbc.transaction {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Global.CREATE_VASU_TEMPLATE
                    )

                    it.insertVasuTemplate(
                        name = body.name,
                        valid = body.valid,
                        type = body.type,
                        language = body.language,
                        content = getDefaultTemplateContent(body.type, body.language)
                    )
                }
            }
            .also { vasuTemplateId -> Audit.VasuTemplateCreate.log(targetId = vasuTemplateId) }
    }

    @PutMapping("/{id}")
    fun editTemplate(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: VasuTemplateId,
        @RequestBody body: VasuTemplateUpdate
    ) {
        db.connect { dbc ->
            dbc.transaction { tx ->
                accessControl.requirePermissionFor(tx, user, clock, Action.VasuTemplate.UPDATE, id)

                val template =
                    tx.getVasuTemplateForUpdate(id) ?: throw NotFound("Template not found")
                validateTemplateUpdate(template, body)
                tx.updateVasuTemplate(id, body)
            }
        }
        Audit.VasuTemplateEdit.log(targetId = id)
    }

    data class CopyTemplateRequest(val name: String, val valid: FiniteDateRange)

    @PostMapping("/{id}/copy")
    fun copyTemplate(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: VasuTemplateId,
        @RequestBody body: CopyTemplateRequest
    ): VasuTemplateId {
        return db.connect { dbc ->
                dbc.transaction {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.VasuTemplate.COPY,
                        id
                    )

                    val template = it.getVasuTemplate(id) ?: throw NotFound("template not found")
                    it.insertVasuTemplate(
                        name = body.name,
                        valid = body.valid,
                        type = template.type,
                        language = template.language,
                        content = copyTemplateContentWithCurrentlyValidOphSections(template)
                    )
                }
            }
            .also { Audit.VasuTemplateCopy.log(targetId = id) }
    }

    @GetMapping
    fun getTemplates(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestParam(required = false) validOnly: Boolean = false
    ): List<VasuTemplateSummary> {
        return db.connect { dbc ->
                dbc.read { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.Global.READ_VASU_TEMPLATE
                    )
                    tx.getVasuTemplates(clock, validOnly)
                }
            }
            .also { Audit.VasuTemplateRead.log(meta = mapOf("count" to it.size)) }
    }

    @GetMapping("/{id}")
    fun getTemplate(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: VasuTemplateId
    ): VasuTemplate {
        return db.connect { dbc ->
                dbc.read { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.VasuTemplate.READ,
                        id
                    )
                    tx.getVasuTemplate(id)
                } ?: throw NotFound("template $id not found")
            }
            .also { Audit.VasuTemplateRead.log(targetId = id) }
    }

    @DeleteMapping("/{id}")
    fun deleteTemplate(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: VasuTemplateId
    ) {
        db.connect { dbc ->
            dbc.transaction {
                accessControl.requirePermissionFor(it, user, clock, Action.VasuTemplate.DELETE, id)
                it.deleteUnusedVasuTemplate(id)
            }
        }
        Audit.VasuTemplateDelete.log(targetId = id)
    }

    @PutMapping("/{id}/content")
    fun putTemplateContent(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: VasuTemplateId,
        @RequestBody content: VasuContent
    ) {
        db.connect { dbc ->
            dbc.transaction { tx ->
                accessControl.requirePermissionFor(tx, user, clock, Action.VasuTemplate.UPDATE, id)
                val template =
                    tx.getVasuTemplateForUpdate(id) ?: throw NotFound("template $id not found")
                if (template.documentCount > 0)
                    throw Conflict("Template with documents cannot be updated", "TEMPLATE_IN_USE")
                tx.updateVasuTemplateContent(id, content)
            }
        }
        Audit.VasuTemplateUpdate.log(targetId = id)
    }
}
