// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.pis.controllers

import fi.espoo.evaka.Audit
import fi.espoo.evaka.pis.Employee
import fi.espoo.evaka.pis.EmployeeWithDaycareRoles
import fi.espoo.evaka.pis.NewEmployee
import fi.espoo.evaka.pis.createEmployee
import fi.espoo.evaka.pis.deleteEmployee
import fi.espoo.evaka.pis.getEmployee
import fi.espoo.evaka.pis.getEmployeeWithRoles
import fi.espoo.evaka.pis.getEmployees
import fi.espoo.evaka.pis.getEmployeesPaged
import fi.espoo.evaka.pis.getFinanceDecisionHandlers
import fi.espoo.evaka.pis.isPinLocked
import fi.espoo.evaka.pis.setEmployeePreferredFirstName
import fi.espoo.evaka.pis.updateEmployee
import fi.espoo.evaka.pis.upsertPinCode
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.Paged
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.EvakaClock
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/employee")
class EmployeeController(private val accessControl: AccessControl) {

    @GetMapping
    fun getEmployees(db: Database, user: AuthenticatedUser, clock: EvakaClock): List<Employee> {
        accessControl.requirePermissionFor(user, clock, Action.Global.READ_EMPLOYEES)
        return db.connect { dbc -> dbc.read { it.getEmployees() } }.sortedBy { it.email }.also {
            Audit.EmployeesRead.log()
        }
    }

    @GetMapping("/finance-decision-handler")
    fun getFinanceDecisionHandlers(db: Database, user: AuthenticatedUser, clock: EvakaClock): List<Employee> {
        accessControl.requirePermissionFor(user, clock, Action.Global.READ_FINANCE_DECISION_HANDLERS)
        return db.connect { dbc -> dbc.read { it.getFinanceDecisionHandlers() } }.sortedBy { it.email }.also {
            Audit.EmployeesRead.log()
        }
    }

    @GetMapping("/{id}")
    fun getEmployee(db: Database, user: AuthenticatedUser.Employee, clock: EvakaClock, @PathVariable(value = "id") id: EmployeeId): Employee {
        accessControl.requirePermissionFor(user, clock, Action.Employee.READ, id)
        return db.connect { dbc -> dbc.read { it.getEmployee(id) } ?: throw NotFound() }.also {
            Audit.EmployeeRead.log(targetId = id)
        }
    }

    data class EmployeeUpdate(
        val globalRoles: List<UserRole>
    )
    @PutMapping("/{id}")
    fun updateEmployee(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable(value = "id") id: EmployeeId,
        @RequestBody body: EmployeeUpdate
    ) {
        accessControl.requirePermissionFor(user, clock, Action.Employee.UPDATE, id)

        db.connect { dbc ->
            dbc.transaction {
                it.updateEmployee(id = id, globalRoles = body.globalRoles)
            }
        }
        Audit.EmployeeUpdate.log(targetId = id, objectId = body.globalRoles)
    }

    @GetMapping("/{id}/details")
    fun getEmployeeDetails(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable(value = "id") id: EmployeeId
    ): EmployeeWithDaycareRoles {
        accessControl.requirePermissionFor(user, clock, Action.Employee.READ_DETAILS, id)

        return db.connect { dbc ->
            dbc.read { it.getEmployeeWithRoles(id) } ?: throw NotFound("employee $id not found")
        }.also {
            Audit.EmployeeRead.log(targetId = id)
        }
    }

    @PostMapping("")
    fun createEmployee(db: Database, user: AuthenticatedUser, clock: EvakaClock, @RequestBody employee: NewEmployee): Employee {
        accessControl.requirePermissionFor(user, clock, Action.Global.CREATE_EMPLOYEE)
        return db.connect { dbc -> dbc.transaction { it.createEmployee(employee) } }.also {
            Audit.EmployeeCreate.log(targetId = employee.externalId)
        }
    }

    @DeleteMapping("/{id}")
    fun deleteEmployee(db: Database, user: AuthenticatedUser, clock: EvakaClock, @PathVariable(value = "id") id: EmployeeId) {
        accessControl.requirePermissionFor(user, clock, Action.Employee.DELETE, id)
        db.connect { dbc -> dbc.transaction { it.deleteEmployee(id) } }
        Audit.EmployeeDelete.log(targetId = id)
    }

    @PostMapping("/pin-code")
    fun upsertPinCode(
        db: Database,
        user: AuthenticatedUser.Employee,
        @RequestBody body: PinCode
    ) {
        db.connect { dbc -> dbc.transaction { tx -> tx.upsertPinCode(user.id, body) } }
        Audit.PinCodeUpdate.log(targetId = user.id)
    }

    @GetMapping("/pin-code/is-pin-locked")
    fun isPinLocked(
        db: Database,
        user: AuthenticatedUser.Employee
    ): Boolean {
        return db.connect { dbc -> dbc.read { tx -> tx.isPinLocked(user.id) } }.also {
            Audit.PinCodeLockedRead.log(targetId = user.id)
        }
    }

    @PostMapping("/search")
    fun searchEmployees(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestBody body: SearchEmployeeRequest
    ): Paged<EmployeeWithDaycareRoles> {
        accessControl.requirePermissionFor(user, clock, Action.Global.SEARCH_EMPLOYEES)
        return db.connect { dbc ->
            dbc.read { tx ->
                getEmployeesPaged(tx, body.page ?: 1, (body.pageSize ?: 50).coerceAtMost(100), body.searchTerm ?: "")
            }
        }.also {
            Audit.EmployeesRead.log()
        }
    }

    data class EmployeePreferredFirstName(
        val preferredFirstName: String?,
        val preferredFirstNameOptions: List<String>
    )
    @GetMapping("/preferred-first-name")
    fun getEmployeePreferredFirstName(
        db: Database,
        user: AuthenticatedUser
    ): EmployeePreferredFirstName {
        return db.connect { dbc ->
            dbc.read { tx ->
                val employee = tx.getEmployee(EmployeeId(user.rawId())) ?: throw NotFound()
                EmployeePreferredFirstName(
                    preferredFirstName = employee.preferredFirstName,
                    preferredFirstNameOptions = possiblePreferredFirstNames(employee)
                )
            }
        }.also {
            Audit.EmployeePreferredFirstNameRead.log()
        }
    }

    data class EmployeeSetPreferredFirstNameUpdateRequest(
        val preferredFirstName: String?
    )
    @PostMapping("/preferred-first-name")
    fun setEmployeePreferredFirstName(
        db: Database,
        user: AuthenticatedUser,
        @RequestBody body: EmployeeSetPreferredFirstNameUpdateRequest
    ) {
        db.connect { dbc ->
            dbc.transaction { tx ->
                val employee = tx.getEmployee(EmployeeId(user.rawId())) ?: throw NotFound()
                if (body.preferredFirstName == null) {
                    tx.setEmployeePreferredFirstName(EmployeeId(user.rawId()), null)
                } else {
                    if (possiblePreferredFirstNames(employee).contains(body.preferredFirstName)) {
                        tx.setEmployeePreferredFirstName(EmployeeId(user.rawId()), body.preferredFirstName)
                    } else throw NotFound("Given preferred first name is not allowed")
                }
            }
        }
        Audit.EmployePreferredFirstNameUpdate.log()
    }

    private fun possiblePreferredFirstNames(employee: Employee): List<String> {
        val fullFirstNames = employee.firstName.split("\\s+".toRegex())
        val splitTwoPartNames = fullFirstNames
            .filter { it.contains('-') }
            .map { it.split("\\-+".toRegex()) }
            .flatten()

        return fullFirstNames + splitTwoPartNames
    }
}

data class PinCode(val pin: String)

data class SearchEmployeeRequest(
    val page: Int?,
    val pageSize: Int?,
    val searchTerm: String?,
)
