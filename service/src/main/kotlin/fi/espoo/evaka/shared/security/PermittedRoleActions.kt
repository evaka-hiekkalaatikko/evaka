// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.security

import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.utils.enumSetOf
import java.util.EnumMap
import java.util.EnumSet

/**
 * Role → action mapping
 */
interface PermittedRoleActions {
    fun attachmentActions(role: UserRole): Set<Action.Attachment>
    fun incomeStatementActions(role: UserRole): Set<Action.IncomeStatement>
    fun parentshipActions(role: UserRole): Set<Action.Parentship>
    fun partnershipActions(role: UserRole): Set<Action.Partnership>
    fun vasuDocumentFollowupActions(role: UserRole): Set<Action.VasuDocumentFollowup>
}

/**
 * Role → action mapping based on static data.
 *
 * Uses system defaults, unless some mappings are overridden using constructor parameters
 */
class StaticPermittedRoleActions(
    val attachment: ActionsByRole<Action.Attachment> = getDefaults(),
    val incomeStatement: ActionsByRole<Action.IncomeStatement> = getDefaults(),
    val parentship: ActionsByRole<Action.Parentship> = getDefaults(),
    val partnership: ActionsByRole<Action.Partnership> = getDefaults(),
    val vasuDocumentFollowup: ActionsByRole<Action.VasuDocumentFollowup> = getDefaults(),
) : PermittedRoleActions {
    override fun attachmentActions(role: UserRole): Set<Action.Attachment> = attachment[role] ?: emptySet()
    override fun incomeStatementActions(role: UserRole): Set<Action.IncomeStatement> = incomeStatement[role] ?: emptySet()
    override fun parentshipActions(role: UserRole): Set<Action.Parentship> = parentship[role] ?: emptySet()
    override fun partnershipActions(role: UserRole): Set<Action.Partnership> = partnership[role] ?: emptySet()
    override fun vasuDocumentFollowupActions(role: UserRole): Set<Action.VasuDocumentFollowup> = vasuDocumentFollowup[role] ?: emptySet()
}

typealias ActionsByRole<A> = Map<UserRole, Set<A>>
typealias RolesByAction<A> = Map<A, Set<UserRole>>

private inline fun <reified A> RolesByAction<A>.invert(): ActionsByRole<A> where A : Action, A : Enum<A> {
    val result = EnumMap<UserRole, EnumSet<A>>(UserRole::class.java)
    this.entries.forEach { (action, roles) ->
        roles.forEach { role ->
            result[role] = EnumSet.copyOf(result[role] ?: enumSetOf()).apply {
                add(action)
            }
        }
    }
    return result
}

private inline fun <reified A> getDefaults() where A : Action.LegacyAction, A : Enum<A> =
    enumValues<A>().associateWith { it.defaultRoles() }.invert()
