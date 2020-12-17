// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.reports

import fi.espoo.evaka.Audit
import fi.espoo.evaka.shared.auth.AccessControlList
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.db.getNullableUUID
import fi.espoo.evaka.shared.db.getUUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
class PlacementSketchingReportController(private val acl: AccessControlList) {
    @GetMapping("/reports/placement-sketching")
    fun getPlacementSketchingReport(
        db: Database,
        user: AuthenticatedUser,
        @RequestParam("placementStartDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) placementStartDate: LocalDate,
        @RequestParam("earliestPreferredStartDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) earliestPreferredStartDate: LocalDate?
    ): ResponseEntity<List<PlacementSketchingReportRow>> {
        Audit.PlacementSketchingReportRead.log()
        user.requireOneOfRoles(UserRole.ADMIN, UserRole.SERVICE_WORKER)
        return db.read { ResponseEntity.ok(it.getPlacementSketchingReportRows(placementStartDate, earliestPreferredStartDate)) }
    }
}

private fun Database.Read.getPlacementSketchingReportRows(
    placementStartDate: LocalDate,
    earliestPreferredStartDate: LocalDate?
): List<PlacementSketchingReportRow> {
    // language=sql
    val sql =
        """
WITH active_placements AS (
SELECT
    placement.child_id AS child_id,
    daycare.name AS daycare_name,
    daycare.id AS daycare_id
FROM
    placement,
    daycare
WHERE
    start_date <= :placementStartDate
    AND end_date >= :placementStartDate
    AND placement.unit_id = daycare.id
)
SELECT
    care_area.name AS area_name,
    daycare.id AS requested_daycare_id,
    daycare.name AS requested_daycare_name,
    application.id AS application_id,
    application.childId,
    application.childfirstname,
    application.childlastname,
    person.date_of_birth,
    application.childstreetaddr,
    active_placements.daycare_name AS current_placement_daycare_name,
    active_placements.daycare_id AS current_placement_daycare_id,
    application.daycareassistanceneeded,
    application.preparatoryeducation,
    application.siblingbasis,
    application.connecteddaycare,
    application.startDate,
    application.sentdate
FROM
    daycare
LEFT JOIN
    care_area ON care_area.id = daycare.care_area_id
LEFT JOIN
    application_view application ON application.preferredunit = daycare.id
LEFT JOIN
    active_placements ON application.childid = active_placements.child_id
LEFT JOIN
    person ON application.childid = person.id
WHERE
    (application.startDate >= :earliestPreferredStartDate OR application.startDate IS NULL)
    AND application.status = ANY ('{SENT,WAITING_PLACEMENT,WAITING_CONFIRMATION,WAITING_DECISION,WAITING_MAILING,WAITING_UNIT_CONFIRMATION, ACTIVE}'::application_status_type[])
    AND application.type = 'preschool'
ORDER BY
    area_name, requested_daycare_name
        """.trimIndent()
    return createQuery(sql)
        .bind("placementStartDate", placementStartDate)
        .bind("earliestPreferredStartDate", earliestPreferredStartDate)
        .map { rs, _ ->
            PlacementSketchingReportRow(
                areaName = rs.getString("area_name"),
                requestedUnitId = rs.getUUID("requested_daycare_id"),
                requestedUnitName = rs.getString("requested_daycare_name"),
                childId = rs.getUUID("childId"),
                childFirstName = rs.getString("childfirstname"),
                childLastName = rs.getString("childlastname"),
                childDob = rs.getString("date_of_birth"),
                childStreetAddr = rs.getString("childstreetaddr"),
                applicationId = rs.getUUID("application_id"),
                currentUnitName = rs.getString("current_placement_daycare_name"),
                currentUnitId = rs.getNullableUUID("current_placement_daycare_id"),
                assistanceNeeded = rs.getBoolean("daycareassistanceneeded"),
                preparatoryEducation = rs.getBoolean("preparatoryeducation"),
                siblingBasis = rs.getBoolean("siblingbasis"),
                connectedDaycare = rs.getBoolean("connecteddaycare"),
                preferredStartDate = rs.getDate("startDate").toLocalDate(),
                sentDate = rs.getDate("sentDate").toLocalDate()
            )
        }.toList()
}

data class PlacementSketchingReportRow(
    val areaName: String,
    val requestedUnitId: UUID,
    val requestedUnitName: String,
    val childId: UUID,
    val childFirstName: String?,
    val childLastName: String?,
    val childDob: String?,
    val childStreetAddr: String?,
    val applicationId: UUID?,
    val currentUnitName: String?,
    val currentUnitId: UUID?,
    val assistanceNeeded: Boolean?,
    val preparatoryEducation: Boolean?,
    val siblingBasis: Boolean?,
    val connectedDaycare: Boolean?,
    val preferredStartDate: LocalDate,
    val sentDate: LocalDate
)
