// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.attendance

import fi.espoo.evaka.backuppickup.getBackupPickupsForChild
import fi.espoo.evaka.dailyservicetimes.DailyServiceTimes
import fi.espoo.evaka.dailyservicetimes.toDailyServiceTimes
import fi.espoo.evaka.daycare.getChild
import fi.espoo.evaka.daycare.service.Absence
import fi.espoo.evaka.daycare.service.AbsenceCareType
import fi.espoo.evaka.daycare.service.AbsenceType
import fi.espoo.evaka.pis.controllers.fetchFamilyContacts
import fi.espoo.evaka.pis.getPersonById
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.placement.getPlacementsForChild
import fi.espoo.evaka.shared.AttendanceId
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.db.mapColumn
import fi.espoo.evaka.shared.domain.FiniteDateRange
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import org.jdbi.v3.core.kotlin.mapTo
import java.time.LocalDate
import java.util.UUID

fun Database.Transaction.insertAttendance(
    childId: UUID,
    unitId: DaycareId,
    arrived: HelsinkiDateTime,
    departed: HelsinkiDateTime? = null
): ChildAttendance {
    // language=sql
    val sql =
        """
        INSERT INTO child_attendance (child_id, unit_id, arrived, departed)
        VALUES (:childId, :unitId, :arrived, :departed)
        RETURNING *
        """.trimIndent()

    return createQuery(sql)
        .bind("childId", childId)
        .bind("unitId", unitId)
        .bind("arrived", arrived)
        .bind("departed", departed)
        .mapTo<ChildAttendance>()
        .first()
}

fun Database.Transaction.insertAbsence(
    user: AuthenticatedUser,
    childId: UUID,
    date: LocalDate,
    careType: AbsenceCareType,
    absenceType: AbsenceType
): Absence {
    // language=sql
    val sql =
        """
        INSERT INTO absence (child_id, date, care_type, absence_type, modified_by)
        VALUES (:childId, :date, :careType, :absenceType, :userId)
        RETURNING *
        """.trimIndent()

    return createQuery(sql)
        .bind("childId", childId)
        .bind("date", date)
        .bind("careType", careType)
        .bind("absenceType", absenceType)
        .bind("userId", user.id)
        .mapTo<Absence>()
        .first()
}

fun Database.Read.getChildAttendance(childId: UUID, unitId: DaycareId, now: HelsinkiDateTime): ChildAttendance? {
    // language=sql
    val sql =
        """
        SELECT id, child_id, unit_id, arrived, departed
        FROM child_attendance
        WHERE child_id = :childId AND unit_id = :unitId
        AND (arrived::date = :date OR tstzrange(arrived, departed) @> :departedThreshold)
        ORDER BY arrived DESC
        LIMIT 1
        """.trimIndent()

    return createQuery(sql)
        .bind("childId", childId)
        .bind("unitId", unitId)
        .bind("date", now.toLocalDate())
        .bind("departedThreshold", now.minusMinutes(30))
        .mapTo<ChildAttendance>()
        .firstOrNull()
}

fun Database.Read.getChildOngoingAttendance(childId: UUID, unitId: DaycareId): ChildAttendance? {
    // language=sql
    val sql =
        """
        SELECT id, child_id, unit_id, arrived, departed
        FROM child_attendance
        WHERE child_id = :childId AND unit_id = :unitId AND departed IS NULL
        """.trimIndent()

    return createQuery(sql)
        .bind("childId", childId)
        .bind("unitId", unitId)
        .mapTo<ChildAttendance>()
        .firstOrNull()
}

data class ChildBasics(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val preferredName: String?,
    val dateOfBirth: LocalDate,
    val dailyServiceTimes: DailyServiceTimes?,
    val placementType: PlacementType,
    val groupId: GroupId,
    val backup: Boolean,
    val imageUrl: String?
)
fun Database.Read.fetchChildrenBasics(unitId: DaycareId, date: LocalDate): List<ChildBasics> {
    // language=sql
    val sql =
        """
        WITH child_group_placement AS (
            SELECT
                gp.daycare_group_id as group_id,
                p.child_id,
                p.type as placement_type,
                false AS backup
            FROM daycare_group_placement gp
            JOIN placement p ON p.id = gp.daycare_placement_id
            WHERE
                p.unit_id = :unitId AND daterange(gp.start_date, gp.end_date, '[]') @> :date AND
                NOT EXISTS (
                    SELECT 1
                    FROM backup_care bc
                    WHERE
                        bc.child_id = p.child_id AND
                        daterange(bc.start_date, bc.end_date, '[]') @> :date
                )

            UNION ALL

            SELECT
                bc.group_id,
                p.child_id,
                p.type as placement_type,
                true AS backup
            FROM backup_care bc
            JOIN placement p ON (
                p.child_id = bc.child_id AND
                daterange(p.start_date, p.end_date, '[]') @> :date
            )
            WHERE
                bc.unit_id = :unitId AND
                bc.group_id IS NOT NULL AND
                daterange(bc.start_date, bc.end_date, '[]') @> :date
        )
        SELECT
            pe.id,
            pe.first_name,
            pe.last_name,
            pe.preferred_name,
            pe.date_of_birth,
            dst.child_id,
            dst.type,
            dst.regular_start,
            dst.regular_end,
            dst.monday_start,
            dst.monday_end,
            dst.tuesday_start,
            dst.tuesday_end,
            dst.wednesday_start,
            dst.wednesday_end,
            dst.thursday_start,
            dst.thursday_end,
            dst.friday_start,
            dst.friday_end,
            cimg.id AS image_id,
            c.group_id,
            c.placement_type,
            c.backup
        FROM child_group_placement c
        JOIN person pe ON pe.id = c.child_id
        LEFT JOIN daily_service_time dst ON dst.child_id = c.child_id
        LEFT JOIN child_images cimg ON pe.id = cimg.child_id
        """.trimIndent()

    return createQuery(sql)
        .bind("unitId", unitId)
        .bind("date", date)
        .map { row ->
            ChildBasics(
                id = row.mapColumn("id"),
                firstName = row.mapColumn("first_name"),
                lastName = row.mapColumn("last_name"),
                preferredName = row.mapColumn("preferred_name"),
                dateOfBirth = row.mapColumn("date_of_birth"),
                placementType = row.mapColumn("placement_type"),
                dailyServiceTimes = toDailyServiceTimes(row),
                groupId = row.mapColumn("group_id"),
                backup = row.mapColumn("backup"),
                imageUrl = row.mapColumn<String?>("image_id")?.let { id -> "/api/internal/child-images/$id" }
            )
        }
        .list()
}

// language=sql
private val placedChildrenSql =
    """
    SELECT p.child_id
    FROM placement p
    WHERE p.unit_id = :unitId AND daterange(p.start_date, p.end_date, '[]') @> :date AND NOT EXISTS (
        SELECT 1 FROM backup_care bc WHERE bc.child_id = p.child_id AND daterange(bc.start_date, bc.end_date, '[]') @> :date
    )
    
    UNION ALL
    
    SELECT bc.child_id
    FROM backup_care bc
    JOIN placement p ON p.child_id = bc.child_id AND daterange(p.start_date, p.end_date, '[]') @> :date
    WHERE bc.unit_id = :unitId AND daterange(bc.start_date, bc.end_date, '[]') @> :date
    """.trimIndent()

fun Database.Read.fetchChildrenAttendances(unitId: DaycareId, now: HelsinkiDateTime): List<ChildAttendance> {
    // language=sql
    val sql =
        """
        SELECT 
            ca.id,
            ca.child_id,
            ca.unit_id,
            ca.arrived,
            ca.departed
        FROM child_attendance ca
        WHERE ca.child_id IN ($placedChildrenSql)
        AND (ca.arrived::date = :date OR tstzrange(ca.arrived, ca.departed) @> :departedThreshold)
        """.trimIndent()

    return createQuery(sql)
        .bind("unitId", unitId)
        .bind("date", now.toLocalDate())
        .bind("departedThreshold", now.minusMinutes(30))
        .mapTo<ChildAttendance>()
        .list()
}

fun Database.Read.fetchChildrenAbsences(unitId: DaycareId, date: LocalDate): List<ChildAbsence> {
    // language=sql
    val sql =
        """
        SELECT 
            ab.id,
            ab.child_id,
            ab.care_type
        FROM absence ab
        WHERE ab.date = :date AND ab.child_id IN ($placedChildrenSql)
        """.trimIndent()

    return createQuery(sql)
        .bind("unitId", unitId)
        .bind("date", date)
        .mapTo<ChildAbsence>()
        .list()
}

fun Database.Transaction.updateAttendance(
    attendanceId: AttendanceId,
    arrived: HelsinkiDateTime,
    departed: HelsinkiDateTime?
) {
    // language=sql
    val sql =
        """
        UPDATE child_attendance
        SET arrived = :arrived, departed = :departed
        WHERE id = :id
        """.trimIndent()

    createUpdate(sql)
        .bind("id", attendanceId)
        .bind("arrived", arrived)
        .bind("departed", departed)
        .execute()
}

fun Database.Transaction.updateAttendanceEnd(attendanceId: AttendanceId, departed: HelsinkiDateTime?) {
    // language=sql
    val sql =
        """
        UPDATE child_attendance
        SET departed = :departed
        WHERE id = :id
        """.trimIndent()

    createUpdate(sql)
        .bind("id", attendanceId)
        .bind("departed", departed)
        .execute()
}

fun Database.Transaction.deleteAttendance(id: AttendanceId) {
    // language=sql
    val sql =
        """
        DELETE FROM child_attendance
        WHERE id = :id
        """.trimIndent()

    createUpdate(sql)
        .bind("id", id)
        .execute()
}

fun Database.Transaction.deleteAbsencesByDate(childId: UUID, date: LocalDate) {
    // language=sql
    val sql =
        """
        DELETE FROM absence
        WHERE child_id = :childId AND date = :date
        """.trimIndent()

    createUpdate(sql)
        .bind("childId", childId)
        .bind("date", date)
        .execute()
}

fun Database.Transaction.deleteAbsencesByFiniteDateRange(childId: UUID, dateRange: FiniteDateRange) {
    // language=sql
    val sql =
        """
        DELETE FROM absence
        WHERE child_id = :childId AND between_start_and_end(:dateRange, date)
        """.trimIndent()

    createUpdate(sql)
        .bind("childId", childId)
        .bind("dateRange", dateRange)
        .execute()
}

fun Database.Read.fetchAttendanceReservations(
    unitId: DaycareId,
    date: LocalDate
): Map<UUID, List<AttendanceReservation>> = createQuery(
    """
    SELECT
        child.id AS child_id,
        to_char((res.start_time AT TIME ZONE 'Europe/Helsinki')::time, 'HH24:MI') AS start_time,
        to_char((res.end_time AT TIME ZONE 'Europe/Helsinki')::time, 'HH24:MI') AS end_time
    FROM attendance_reservation res
    JOIN person child ON res.child_id = child.id
    JOIN placement ON child.id = placement.child_id AND res.start_date BETWEEN placement.start_date AND placement.end_date
    WHERE res.start_date = :date AND placement.unit_id = :unitId
    """.trimIndent()
)
    .bind("unitId", unitId)
    .bind("date", date)
    .map { ctx ->
        ctx.mapColumn<UUID>("child_id") to AttendanceReservation(
            ctx.mapColumn("start_time"),
            ctx.mapColumn("end_time")
        )
    }
    .groupBy { (childId, _) -> childId }
    .mapValues { it.value.map { (_, reservation) -> reservation } }

fun Database.Read.getChildSensitiveInfo(childId: UUID): ChildSensitiveInformation? {
    val person = getPersonById(childId)
    val placementTypes = getPlacementsForChild(childId).map { it.type }
    val child = getChild(childId)
    val backupPickups = getBackupPickupsForChild(childId)
    val familyContacts = fetchFamilyContacts(childId)

    return if (person != null) {
        ChildSensitiveInformation(
            id = person.id,
            firstName = person.firstName,
            lastName = person.lastName,
            preferredName = child?.additionalInformation?.preferredName ?: "",
            ssn = person.identity.toString(),
            childAddress = person.streetAddress,
            placementTypes = placementTypes,
            allergies = child?.additionalInformation?.allergies ?: "",
            diet = child?.additionalInformation?.diet ?: "",
            medication = child?.additionalInformation?.medication ?: "",
            contacts = familyContacts.filter { it.priority != null }.sortedBy { it.priority }.map {
                ContactInfo(
                    id = it.id.toString(),
                    firstName = it.firstName,
                    lastName = it.lastName,
                    phone = it.phone,
                    backupPhone = it.backupPhone,
                    email = it.email ?: "",
                    priority = it.priority
                )
            },
            backupPickups = backupPickups.map {
                ContactInfo(
                    id = it.id.toString(),
                    firstName = it.name,
                    lastName = "",
                    phone = it.phone,
                    backupPhone = "",
                    email = "",
                    priority = 1
                )
            }
        )
    } else
        return null
}
