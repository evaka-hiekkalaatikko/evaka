// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.security.actionrule

import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.MobileDeviceId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.MobileAuthLevel
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.security.AccessControlDecision
import org.jdbi.v3.core.kotlin.mapTo

private typealias FilterGroupByMobile<T> = (tx: Database.Read, mobileId: MobileDeviceId, targets: Set<T>) -> Iterable<T>

data class IsMobileInGroupUnit(val requirePinLogin: Boolean) : ActionRuleParams<IsMobileInGroupUnit> {
    override fun merge(other: IsMobileInGroupUnit): IsMobileInGroupUnit =
        IsMobileInGroupUnit(this.requirePinLogin && other.requirePinLogin)

    private data class Query<T>(private val filter: FilterGroupByMobile<T>) : DatabaseActionRule.Query<T, IsMobileInGroupUnit> {
        override fun execute(tx: Database.Read, user: AuthenticatedUser, targets: Set<T>): Map<T, DatabaseActionRule.Deferred<IsMobileInGroupUnit>> = when (user) {
            is AuthenticatedUser.MobileDevice -> filter(tx, MobileDeviceId(user.id), targets).associateWith { Deferred(user.authLevel) }
            else -> emptyMap()
        }
    }
    private data class Deferred(private val authLevel: MobileAuthLevel) : DatabaseActionRule.Deferred<IsMobileInGroupUnit> {
        override fun evaluate(params: IsMobileInGroupUnit): AccessControlDecision =
            if (params.requirePinLogin && authLevel != MobileAuthLevel.PIN_LOGIN) {
                AccessControlDecision.Denied(params, "PIN login required", "PIN_LOGIN_REQUIRED")
            } else {
                AccessControlDecision.Permitted(params)
            }
    }

    val group = DatabaseActionRule(
        this,
        Query<GroupId> { tx, mobileId, ids ->
            tx.createQuery(
                """
SELECT daycare_group_id AS id, role
FROM daycare_group_acl_view
WHERE employee_id = :userId
AND role = 'MOBILE'
AND daycare_group_id = ANY(:ids)
                """.trimIndent()
            )
                .bind("ids", ids.toTypedArray())
                .bind("userId", mobileId)
                .mapTo()
        }
    )
}
