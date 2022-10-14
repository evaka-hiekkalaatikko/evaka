// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.pis

import fi.espoo.evaka.Audit
import fi.espoo.evaka.ExcludeCodeGen
import fi.espoo.evaka.identity.ExternalId
import fi.espoo.evaka.identity.ExternalIdentifier
import fi.espoo.evaka.pairing.MobileDeviceIdentity
import fi.espoo.evaka.pairing.getDeviceByToken
import fi.espoo.evaka.pis.service.PersonService
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import fi.espoo.evaka.shared.security.EmployeeFeatures
import fi.espoo.evaka.shared.security.upsertCitizenUser
import fi.espoo.evaka.shared.security.upsertEmployeeUser
import fi.espoo.evaka.shared.security.upsertMobileDeviceUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Controller for "system" endpoints intended to be only called from apigw as the system internal user
 */
@RestController
class SystemController(private val personService: PersonService, private val accessControl: AccessControl) {
    @PostMapping("/system/citizen-login")
    fun citizenLogin(
        db: Database,
        user: AuthenticatedUser.SystemInternalUser,
        clock: EvakaClock,
        @RequestBody request: CitizenLoginRequest
    ): CitizenUser {
        return db.connect { dbc ->
            dbc.transaction { tx ->
                val citizen = tx.getCitizenUserBySsn(request.socialSecurityNumber)
                    ?: personService.getOrCreatePerson(
                        tx,
                        user,
                        ExternalIdentifier.SSN.getInstance(request.socialSecurityNumber)
                    )?.let { CitizenUser(it.id) }
                    ?: error("No person found with ssn")
                tx.markPersonLastLogin(clock, citizen.id)
                tx.upsertCitizenUser(citizen.id)
                personService.getPersonWithChildren(tx, user, citizen.id)
                citizen
            }
        }.also {
            Audit.CitizenLogin.log(targetId = listOf(request.socialSecurityNumber, request.lastName, request.firstName), objectId = it.id)
        }
    }

    @PostMapping("/system/employee-login")
    fun employeeLogin(
        db: Database,
        user: AuthenticatedUser.SystemInternalUser,
        clock: EvakaClock,
        @RequestBody request: EmployeeLoginRequest
    ): EmployeeUser {
        return db.connect { dbc ->
            dbc.transaction {
                if (request.employeeNumber != null) {
                    it.updateExternalIdByEmployeeNumber(request.employeeNumber, request.externalId)
                }
                val inserted = it.loginEmployee(clock, request.toNewEmployee())
                val roles = it.getEmployeeRoles(inserted.id)
                val employee = EmployeeUser(
                    id = inserted.id,
                    firstName = inserted.preferredFirstName ?: inserted.firstName,
                    lastName = inserted.lastName,
                    globalRoles = roles.globalRoles,
                    allScopedRoles = roles.allScopedRoles
                )
                it.upsertEmployeeUser(employee.id)
                employee
            }
        }.also {
            Audit.EmployeeLogin.log(targetId = listOf(request.externalId, request.lastName, request.firstName, request.email, it.globalRoles), objectId = it.id)
        }
    }

    @GetMapping("/system/employee/{id}")
    fun employeeUser(
        db: Database,
        systemUser: AuthenticatedUser.SystemInternalUser,
        clock: EvakaClock,
        @PathVariable
        id: EmployeeId
    ): EmployeeUserResponse? {
        return db.connect { dbc ->
            dbc.read { tx ->
                tx.getEmployeeUser(id)?.let { employeeUser ->
                    val user = AuthenticatedUser.Employee(employeeUser)
                    val permittedGlobalActions = accessControl.getPermittedActions<Action.Global>(tx, user, clock)
                    val accessibleFeatures = EmployeeFeatures(
                        applications = permittedGlobalActions.contains(Action.Global.APPLICATIONS_PAGE),
                        employees = permittedGlobalActions.contains(Action.Global.EMPLOYEES_PAGE),
                        financeBasics = permittedGlobalActions.contains(Action.Global.FINANCE_BASICS_PAGE),
                        finance = permittedGlobalActions.contains(Action.Global.FINANCE_PAGE),
                        holidayPeriods = permittedGlobalActions.contains(Action.Global.HOLIDAY_PERIODS_PAGE),
                        messages = accessControl.checkPermissionFor(tx, user, clock, Action.Global.MESSAGES_PAGE, allowedToAdmin = false).isPermitted(),
                        personSearch = permittedGlobalActions.contains(Action.Global.PERSON_SEARCH_PAGE),
                        reports = permittedGlobalActions.contains(Action.Global.REPORTS_PAGE),
                        settings = permittedGlobalActions.contains(Action.Global.SETTINGS_PAGE),
                        unitFeatures = permittedGlobalActions.contains(Action.Global.UNIT_FEATURES_PAGE),
                        units = permittedGlobalActions.contains(Action.Global.UNITS_PAGE),
                        createUnits = permittedGlobalActions.contains(Action.Global.CREATE_UNIT),
                        vasuTemplates = permittedGlobalActions.contains(Action.Global.VASU_TEMPLATES_PAGE),
                        personalMobileDevice = permittedGlobalActions.contains(Action.Global.PERSONAL_MOBILE_DEVICE_PAGE),
                        pinCode = permittedGlobalActions.contains(Action.Global.PIN_CODE_PAGE),
                        assistanceNeedDecisionsReport = permittedGlobalActions.contains(Action.Global.READ_ASSISTANCE_NEED_DECISIONS_REPORT),
                        createDraftInvoices = permittedGlobalActions.contains(Action.Global.CREATE_DRAFT_INVOICES)
                    )

                    EmployeeUserResponse(
                        id = employeeUser.id,
                        firstName = employeeUser.preferredFirstName ?: employeeUser.firstName,
                        lastName = employeeUser.lastName,
                        globalRoles = employeeUser.globalRoles,
                        allScopedRoles = employeeUser.allScopedRoles,
                        accessibleFeatures = accessibleFeatures,
                        permittedGlobalActions = permittedGlobalActions
                    )
                }
            }
        }.also {
            Audit.EmployeeGetOrCreate.log(targetId = id)
        }
    }

    @GetMapping("/system/mobile-identity/{token}")
    fun mobileIdentity(
        db: Database,
        user: AuthenticatedUser.SystemInternalUser,
        @PathVariable
        token: UUID
    ): MobileDeviceIdentity {
        return db.connect { dbc ->
            dbc.transaction { tx ->
                val device = tx.getDeviceByToken(token)
                tx.upsertMobileDeviceUser(device.id)
                device
            }
        }.also {
            Audit.MobileDevicesRead.log(targetId = token, objectId = it.id)
        }
    }

    @ExcludeCodeGen
    data class EmployeeLoginRequest(
        val externalId: ExternalId,
        val firstName: String,
        val lastName: String,
        val employeeNumber: String?,
        val email: String?
    ) {
        fun toNewEmployee(): NewEmployee =
            NewEmployee(firstName = firstName, lastName = lastName, email = email, externalId = externalId, employeeNumber = employeeNumber)
    }

    @ExcludeCodeGen
    data class CitizenLoginRequest(
        val socialSecurityNumber: String,
        val firstName: String,
        val lastName: String
    )

    data class EmployeeUserResponse(
        val id: EmployeeId,
        val firstName: String,
        val lastName: String,
        val globalRoles: Set<UserRole> = setOf(),
        val allScopedRoles: Set<UserRole> = setOf(),
        val accessibleFeatures: EmployeeFeatures,
        val permittedGlobalActions: Set<Action.Global>
    )
}
