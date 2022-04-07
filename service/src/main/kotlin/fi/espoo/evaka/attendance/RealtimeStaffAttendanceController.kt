// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.attendance

import fi.espoo.evaka.Audit
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.StaffAttendanceExternalId
import fi.espoo.evaka.shared.StaffAttendanceId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.FiniteDateRange
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/staff-attendances/realtime")
class RealtimeStaffAttendanceController(
    private val ac: AccessControl
) {
    @GetMapping
    fun getAttendances(
        db: Database,
        user: AuthenticatedUser,
        @RequestParam unitId: DaycareId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) start: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) end: LocalDate
    ): StaffAttendanceResponse {
        Audit.StaffAttendanceRead.log(targetId = unitId)

        ac.requirePermissionFor(user, Action.Unit.READ_STAFF_ATTENDANCES, unitId)

        return db.connect { dbc ->
            dbc.read {
                val range = FiniteDateRange(start, end)
                val attendancesByEmployee = it.getStaffAttendancesForDateRange(unitId, range).groupBy { raw -> raw.employeeId }
                val staffWithAttendance = attendancesByEmployee.entries.map { (employeeId, data) ->
                    EmployeeAttendance(
                        employeeId = employeeId,
                        groups = it.getGroupsForEmployee(employeeId),
                        firstName = data[0].firstName,
                        lastName = data[0].lastName,
                        currentOccupancyCoefficient = data[0].currentOccupancyCoefficient ?: BigDecimal.ZERO,
                        attendances = data.map { att -> Attendance(att.id, att.groupId, att.arrived, att.departed, att.occupancyCoefficient) }
                    )
                }
                val staffWithoutAttendance = it.getCurrentStaffForAttendanceCalendar(unitId)
                    .filter { emp -> !attendancesByEmployee.keys.contains(emp.id) }
                    .map { emp ->
                        EmployeeAttendance(
                            employeeId = emp.id,
                            groups = it.getGroupsForEmployee(emp.id),
                            firstName = emp.firstName,
                            lastName = emp.lastName,
                            currentOccupancyCoefficient = emp.currentOccupancyCoefficient ?: BigDecimal.ZERO, listOf()
                        )
                    }
                StaffAttendanceResponse(
                    staff = staffWithAttendance + staffWithoutAttendance,
                    extraAttendances = it.getExternalStaffAttendancesByDateRange(unitId, range)
                )
            }
        }
    }

    data class UpdateStaffAttendanceRequest(
        val attendanceId: StaffAttendanceId,
        val arrived: HelsinkiDateTime,
        val departed: HelsinkiDateTime?
    )

    @PostMapping("/{unitId}/update")
    fun updateStaffAttendance(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable unitId: DaycareId,
        @RequestBody body: UpdateStaffAttendanceRequest
    ) {
        Audit.StaffAttendanceUpdate.log(targetId = unitId)

        ac.requirePermissionFor(user, Action.Unit.UPDATE_STAFF_ATTENDANCES, unitId)

        db.connect { dbc ->
            dbc.transaction {
                it.updateStaffAttendance(body.attendanceId, body.arrived, body.departed)
            }
        }
    }

    data class UpdateExternalAttendanceRequest(
        val attendanceId: StaffAttendanceExternalId,
        val arrived: HelsinkiDateTime,
        val departed: HelsinkiDateTime?
    )

    @PostMapping("/{unitId}/update-external")
    fun updateExternalAttendance(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable unitId: DaycareId,
        @RequestBody body: UpdateExternalAttendanceRequest
    ) {
        Audit.StaffAttendanceUpdate.log(targetId = unitId)

        ac.requirePermissionFor(user, Action.Unit.UPDATE_STAFF_ATTENDANCES, unitId)

        db.connect { dbc ->
            dbc.transaction {
                it.updateExternalStaffAttendance(body.attendanceId, body.arrived, body.departed)
            }
        }
    }
}
