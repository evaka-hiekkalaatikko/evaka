// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.placement

import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.PlacementPlanId
import fi.espoo.evaka.shared.domain.FiniteDateRange
import java.time.LocalDate
import java.util.UUID

data class PlacementPlan(
    val id: PlacementPlanId,
    val unitId: DaycareId,
    val applicationId: ApplicationId,
    val type: PlacementType,
    val period: FiniteDateRange,
    val preschoolDaycarePeriod: FiniteDateRange?
)

data class PlacementPlanDetails(
    val id: PlacementPlanId,
    val unitId: DaycareId,
    val applicationId: ApplicationId,
    val type: PlacementType,
    val period: FiniteDateRange,
    val preschoolDaycarePeriod: FiniteDateRange?,
    val child: PlacementPlanChild,
    val unitConfirmationStatus: PlacementPlanConfirmationStatus = PlacementPlanConfirmationStatus.PENDING,
    val unitRejectReason: PlacementPlanRejectReason? = null,
    val unitRejectOtherReason: String? = null,
    val rejectedByCitizen: Boolean = false
)

data class PlacementPlanChild(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate
)

enum class PlacementPlanConfirmationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    REJECTED_NOT_CONFIRMED
}

enum class PlacementPlanRejectReason {
    OTHER,
    REASON_1,
    REASON_2,
    REASON_3
}
